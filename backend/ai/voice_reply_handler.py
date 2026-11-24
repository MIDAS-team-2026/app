"""
음성 업로드 후 AI 답변 생성 및 회상 질문 저장 로직.

핵심 흐름:
1. 앱은 AI가 인사말과 함께 첫 고정 질문을 먼저 말하는 구조이므로, 사용자의 첫 발화는
   그 첫 고정 질문에 대한 답변으로 채점된다.
2. 초기 고정 질문은 FIXED로 저장하며, 하루에 한 번만 노출한다(오늘 이미 완료했다면 건너뛴다).
3. FIXED / INITIAL / RECALL 답변은 새로운 memoryPoint 후보에서 제외한다.
4. 충분한 memoryPoint가 쌓이고 현재 대화가 회상 전환에 적절할 때 회상 질문을 생성한다.
5. 회상 질문 답변은 RECALL로 저장하고, 이후 다시 자유대화로 복귀한다.
"""

import hashlib
import logging
from time import perf_counter
import os
import re
import requests
from dataclasses import dataclass
from datetime import date

from kiwipiepy import Kiwi

from recall.free_talk_question_generator import (
    generate_safe_followup_question,
    is_similar_to_previous_question,
)
from recall.conversation_policy import (
    ConversationAction,
    ConversationDecision,
    ConversationReply,
    RecallTimingAction,
    MAX_CONSECUTIVE_TOPIC_TURNS,
    decide_conversation_action,
    decide_recall_timing,
)
from recall.conversation_recall_generator import (
    extract_memory_action_concepts,
    generate_and_save_recall_question,
    score_recall_memory_candidate,
    select_recall_memory_candidates,
)
from recall.memory_candidate_service import build_memory_candidate_selection
from recall.episode_context import active_episode_records, episode_identity, episode_segments, ordered_records, records_through_current
from recall.followup_intent import FollowupIntentState, build_followup_intent_state, render_event_followups, infer_followup_target
from recall.dialogue_act import conversation_control, is_acknowledgement, is_clarification_request, is_repeat_reminder, is_greeting
from recall.dialogue_act import classify_recall_response, RecallResponseIntent
from recall.event_continuity import answers_event_detail
from recall.event_modality import EventFrame, EventMode, NON_EXPERIENCED, resolve_event_frames, is_current_state
from recall.uncertain_reply import short_reference, declines_detail, resolve_short_reference
from recall.recall_scheduler import select_pending_recall, turn_position, memory_wait_turns, memory_wait_seconds, scheduled_recall_turn, recall_is_due, session_recall_history
from recall.recall_context import clarify_recall_question
from recall.llm_dialogue_prototype import propose_dialogue
from recall.dialogue_response_experiment import propose_reply_only, validate_local_voice_reply
from recall.voice_fallback import fallback_candidates
from recall.stage_timing import timed_stage
from recall.record_execution import serialize_record, serialize_session
from recall.recall_presentation import presented_question_matches
from recall.memory_event import (
    MemoryAnswerType,
    infer_answer_type,
    should_continue_memory_event,
)
from recall.memory_evidence_extractor import (
    CONTEXT_ONLY_PLACES,
    extract_action_object,
    extract_memory_clues,
    extract_predicate_forms,
    is_correction_utterance,
)
from recall.recall_api_client import analyze_session_recall

logger = logging.getLogger(__name__)
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


class DialogueStateUnavailable(RuntimeError):
    """Required persisted dialogue state could not be read reliably."""


@dataclass(frozen=True)
class ConversationTurnState:
    latest_text: str
    conversation_history: tuple[str, ...]
    previous_question: str
    topic: str | None
    consecutive_topic_turns: int
    after_recall_answer: bool
    should_change_topic: bool
    is_memory_candidate: bool
    needs_memory_detail: bool
    has_real_content: bool
    has_anchorable_event: bool
    has_sufficient_detail: bool = False
    is_unanswered: bool = False
    event_frame: EventFrame = EventFrame()
    followup_intent: FollowupIntentState | None = None


@dataclass(frozen=True)
class RecallCandidateContext:
    conversation_history: tuple[str, ...] = ()
    source_record_ids: tuple[tuple[str, int], ...] = ()
    memory_candidates: tuple[dict, ...] = ()
    prior_wait_turns: int = 0
    prior_wait_seconds: float | None = None

    def find_source_record_id(self, source_text: str) -> int | None:
        normalized_source = _normalize_text(source_text)

        for text, record_id in self.source_record_ids:
            if _normalize_text(text) == normalized_source:
                return record_id

        return None

    def find_memory_candidate(self, candidate: dict) -> dict | None:
        if not isinstance(candidate, dict):
            return None

        try:
            identity = (
                str(candidate.get("eventId") or "").strip(),
                int(candidate.get("sourceRecordId") or 0),
                _normalize_text(candidate.get("sourceText") or ""),
                str(candidate.get("answerType") or "").strip().upper(),
                _normalize_text(candidate.get("answerValue") or ""),
            )
        except (TypeError, ValueError):
            return None

        for expected in self.memory_candidates:
            expected_identity = (
                str(expected.get("eventId") or "").strip(),
                int(expected.get("sourceRecordId") or 0),
                _normalize_text(expected.get("sourceText") or ""),
                str(expected.get("answerType") or "").strip().upper(),
                _normalize_text(expected.get("answerValue") or ""),
            )

            if identity == expected_identity:
                return expected

        return None


FIXED_QUESTIONS = [
    {
        "questionText": "성함을 어떻게 불러드리면 될까요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "USER_NAME",
    },
    {
        "questionText": "배우자분 성함은 어떻게 되세요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "SPOUSE_NAME",
    },
    {
        "questionText": "고향은 어디세요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "HOMETOWN",
    },
    {
        "questionText": "오늘이 무슨 요일인지 기억나시나요?",
        "questionType": "ORIENTATION",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "TODAY_WEEKDAY",
    },
    {
        "questionText": "오늘 날짜가 며칠인지 기억나세요?",
        "questionType": "ORIENTATION",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "TODAY_DATE",
    },
]


SAFE_OPENING_QUESTIONS = [
    "요즘 제일 보고 싶은 사람은 누구세요?",
    "오늘 드신 것 중에 기억나는 음식이 있으세요?",
    "오늘 집 밖에 다녀오신 곳이 있으세요?",
    "오늘 집에서는 주로 어떻게 시간을 보내셨어요?",
    "최근에 본 방송이나 들은 노래 중 기억나는 게 있으세요?",
    "요즘 동네에서 자주 보이는 풍경이 있으세요?",
    "최근에 손에 자주 잡는 물건이 있으세요?",
    "요즘 날씨를 보면 떠오르는 일이 있으세요?",
    "예전에 자주 하시던 일 중에 요즘 생각나는 게 있으세요?",
    "최근에 시장이나 마트에서 장을 보신 적이 있으세요?",
    "집 안에서 가장 오래 머무는 자리가 어디세요?",
    "요즘 하루 중 기다려지는 시간이 있으세요?",
]


# 고정 질문 답변 직후 자유대화로 넘어갈 때 쓰는 쿠션 문장.
# 고정 질문 답변 내용(성함/배우자/고향 등)과 무관하게, 방금 답변에
# 자연스럽게 반응하면서 자유대화로 전환만 시키는 범용 문장만 담는다.
FIXED_TO_FREE_TALK_OPENERS = [
    "오늘 식사는 하셨어요?",
    "오늘 집에서는 무엇을 하셨어요?",
    "오늘 보신 방송이 있으세요?",
    "오늘 누구와 이야기 나누셨어요?",
]


AFTER_RECALL_OPENING_QUESTIONS = [
    "이어서 오늘 드신 것 중에 생각나는 음식이 있으세요?",
    "이번에는 오늘 집에서 하신 일 중에 하나 말씀해주실래요?",
    "이번에는 최근에 본 방송이나 들은 노래 이야기도 해볼까요?",
    "이어서 오늘 밖이나 창밖에서 본 것이 있으세요?",
    "이번에는 손에 잡았던 물건이나 하셨던 일이 있으세요?",
    "이어서 오늘 연락하거나 만나신 사람이 있으세요?",
]

AFTER_LOW_INFO_RECALL_OPENING_QUESTIONS = [
    "아하, 그렇군요. 오늘 집에서 하신 일 중에 편하게 떠오르는 게 있으세요?",
    "그러셨군요. 이번에는 오늘 보신 방송이나 들은 소리 이야기를 해볼까요?",
    "알겠습니다. 그러면 오늘 드신 것 중에 기억나는 음식이 있으세요?",
]

AFTER_NEGATIVE_RECALL_OPENING_QUESTIONS = [
    "그러셨군요. 그럼 오늘 조금이라도 편했던 순간이 있으세요?",
    "그랬군요. 지금은 편하게 이야기할 수 있는 다른 일이 있으세요?",
    "알겠습니다. 오늘 집에서 마음이 조금 편했던 시간이 있으세요?",
]

AFTER_SHORT_RECALL_OPENING_QUESTIONS = [
    "그렇군요. 그럼 오늘 집에서 하신 일 중에 하나 말씀해주실래요?",
    "알겠습니다. 이번에는 오늘 보신 것 중에 기억나는 게 있으세요?",
    "그러셨군요. 오늘 드신 것 중에 생각나는 음식이 있으세요?",
]

LOW_BURDEN_TOPIC_CHANGE_QUESTIONS = [
    "오늘 집에서 하신 일 중에 하나 말씀해주실래요?",
    "오늘 보신 방송이나 들은 소리가 있으세요?",
    "오늘 손에 잡았던 물건이나 하신 일이 있으세요?",
]

REPEATED_LOW_INFO_QUESTIONS = {
    "DEEPEN": [
        "아하, 그렇군요. 그럼 오늘 드신 음식 중 하나만 말씀해주실래요?",
        "그러셨군요. 그럼 오늘 집에서 하신 일이나 보신 것 중 편한 것부터 말씀해주실래요?",
        "알겠습니다. 그럼 오늘 보신 방송이나 들은 노래가 있으세요?",
    ],
    "ANCHOR": [
        "아하, 그렇군요. 오늘 손에 자주 잡았던 물건이 하나 있으세요?",
        "그러셨군요. 오늘 기억나는 음식이나 방송 중 편한 것부터 이야기해볼까요?",
        "알겠습니다. 오늘 집에서 가장 오래 머문 자리가 어디였어요?",
    ],
}


SAFE_STAGE_FALLBACK_QUESTIONS = {
    "DEEPEN": [
        "조금 더 말해주시면, 그때는 어디에 계셨어요?",
        "그때 혼자 계셨어요, 아니면 누군가와 같이 계셨어요?",
        "그때 모습 중에 제일 먼저 떠오르는 게 있으세요?",
        "그때가 하루 중 언제쯤이었는지 기억나세요?",
        "그때 주변에 보였던 것이 하나라도 떠오르세요?",
        "그 이야기를 하다 보니 또 생각나는 게 있으세요?",
    ],
    "ANCHOR": [
        "나중에 다시 떠올릴 만한 장면이 하나 있으세요?",
        "그때 함께 있던 사람이나 장소가 기억나세요?",
        "그때 가장 먼저 생각나는 모습이 있으세요?",
        "방금 이야기한 걸 한 가지 장면으로 말하면 어떤 모습일까요?",
        "그때 계셨던 곳 주변에 뭐가 있었나요?",
        "나중에 다시 이야기한다면 어떤 말로 떠올리면 좋을까요?",
    ],
}


NO_EVENT_FALLBACK_QUESTIONS = [
    "그럼 오늘은 어떻게 시간을 보내고 계세요?",
    "오늘 다른 이야기도 편하게 들려주시겠어요?",
    "지금 주변에서 보이는 것 중에 이야기해볼 만한 게 있으세요?",
    "오늘 집에서 하신 일 중에 하나만 말씀해주실래요?",
]

TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS = {
    "LOW_INFO": {
        "DEEPEN": [
            "아하, 그러셨군요. 그럼 오늘 드신 것 중에 기억나는 음식이 있으세요?",
            "그러셨군요. 오늘 집에서 하신 일 중에 하나 떠오르는 게 있으세요?",
            "음, 그렇군요. 오늘 보신 것 중에 기억나는 장면이 있으세요?",
        ],
        "ANCHOR": [
            "오늘 손에 자주 잡았던 물건이 하나 있으세요?",
            "오늘 집에서 가장 오래 머문 자리가 어디였어요?",
            "방금 이야기 말고 오늘 드신 음식 중 하나가 떠오르세요?",
        ],
    },
    "NEGATIVE": {
        "DEEPEN": [
            "그러셨군요. 무엇 때문에 기분이 조금 가라앉으셨어요?",
            "그럴 때는 무엇을 하면 마음이 조금 편해지세요?",
            "오늘 조금이라도 마음이 편했던 순간이 있으세요?",
        ],
        "ANCHOR": [
            "그때 기분을 떠올리면 가장 먼저 생각나는 장면이 있으세요?",
            "오늘 마음이 무거웠던 일 중에 기억나는 게 있으세요?",
            "그 이야기를 나중에 떠올리면 어떤 말이 먼저 생각날까요?",
        ],
    },
    "WEATHER": {
        "DEEPEN": [
            "더울 때는 집 안에서 어떻게 지내셨어요?",
            "오늘 날씨 때문에 불편했던 점이 있으셨어요?",
            "그때 창밖이나 주변에서 보인 게 있으세요?",
        ],
        "ANCHOR": [
            "오늘 날씨를 떠올리면 가장 먼저 생각나는 장면이 있으세요?",
            "더웠던 오늘 중에 기억나는 시간이 있으세요?",
            "오늘 밖이나 창밖에서 본 것이 기억나세요?",
        ],
    },
    "SHOPPING": {
        "DEEPEN": [
            "사신 것 중에 가장 기억나는 물건이 있으세요?",
            "그 물건은 어디에서 고르셨어요?",
            "사신 건 지금 어디에 두셨는지 기억나세요?",
        ],
        "ANCHOR": [
            "오늘 사신 것 중에 나중에 기억할 만한 게 있으세요?",
            "장 보던 일을 떠올리면 제일 먼저 생각나는 게 무엇인가요?",
            "그때 고른 물건 중에 기억나는 게 있으세요?",
        ],
    },
    "OBJECT": {
        "DEEPEN": [
            "그 물건은 주로 언제 사용하세요?",
            "그 물건은 집에서 주로 어디에 두세요?",
            "그 물건을 사용할 때 주로 무엇을 하세요?",
        ],
        "ANCHOR": [
            "그 물건을 떠올리면 어떤 모습이 먼저 생각나세요?",
            "오늘 그 물건을 어디에서 사용하셨어요?",
            "그 물건과 관련해서 오늘 기억나는 일이 있으세요?",
        ],
    },
    "ROUTINE": {
        "DEEPEN": [
            "그 시간에는 보통 무엇을 하세요?",
            "그 시간이 기다려지는 이유가 있으세요?",
            "그때는 주로 누구와 함께 계세요?",
        ],
        "ANCHOR": [
            "그 시간에 가장 자주 하는 일이 무엇인가요?",
            "그 시간을 떠올리면 어떤 모습이 먼저 생각나세요?",
            "오늘도 그 시간에 하신 일이 있으세요?",
        ],
    },
    "SCENERY": {
        "DEEPEN": [
            "그 풍경은 주로 어디에서 보세요?",
            "그 풍경에서 가장 눈에 띄는 것이 무엇인가요?",
            "그 모습을 볼 때 기분은 어떠세요?",
        ],
        "ANCHOR": [
            "오늘도 그 풍경을 보신 시간이 있으세요?",
            "그 풍경을 떠올리면 어떤 색이나 모습이 먼저 생각나세요?",
            "그 풍경에서 오늘 기억나는 변화가 있으세요?",
        ],
    },
    "HEALTH": {
        "DEEPEN": [
            "약은 언제쯤 드셨어요?",
            "약 드신 뒤에는 몸이 좀 어떠셨어요?",
            "병원에는 혼자 다녀오셨어요, 아니면 누군가와 함께 가셨어요?",
            "그때 몸 상태는 어떠셨어요?",
            "어느 쪽이 제일 불편하셨어요?",
            "지금은 조금 괜찮으세요?",
            "지금도 움직이실 때 불편하세요?",
            "병원에 계실 때 기억나는 일이 있으세요?",
        ],
        "ANCHOR": [
            "오늘 몸이나 병원 이야기 중에 기억나는 점이 있으세요?",
            "약이나 병원 이야기를 떠올리면 제일 먼저 생각나는 게 있으세요?",
            "그때 있었던 일 중에 기억나는 장면이 있으세요?",
        ],
    },
    "FOOD": {
        "DEEPEN": [
            "그때 누구와 같이 드셨어요?",
            "그 음식은 어디에서 드셨어요?",
            "드셨을 때 맛은 어떠셨어요?",
        ],
        "ANCHOR": [
            "그 음식에서 가장 기억나는 점이 있으세요?",
            "그때 드신 음식의 맛이나 모습 중에 먼저 떠오르는 게 있으세요?",
            "그 음식을 떠올리면 제일 먼저 생각나는 게 무엇인가요?",
        ],
    },
    "PERSON": {
        "DEEPEN": [
            "그분과는 최근에 어떤 이야기를 나누셨어요?",
            "그분이 생각날 때 가장 먼저 떠오르는 모습이 있으세요?",
            "그분과 함께했던 일 중에 기억나는 장면이 있으세요?",
        ],
        "ANCHOR": [
            "그분을 떠올리면 제일 먼저 생각나는 모습이 있으세요?",
            "그분과의 이야기 중에 지금도 기억나는 게 있으세요?",
            "그분과 다시 이야기한다면 어떤 말이 먼저 떠오르세요?",
        ],
    },
    "PLACE": {
        "DEEPEN": [
            "그곳에는 혼자 가셨어요, 아니면 누군가와 함께 가셨어요?",
            "그곳에서 가장 먼저 보였던 것이 있으세요?",
            "그곳에 계셨을 때 주변 분위기는 어떠셨어요?",
        ],
        "ANCHOR": [
            "그곳에서 기억나는 장면이 있으세요?",
            "그 장소를 떠올리면 제일 먼저 생각나는 게 있으세요?",
            "그때 주변에서 본 것이 기억나세요?",
        ],
    },
    "HOME": {
        "DEEPEN": [
            "집에서는 주로 무엇을 하며 시간을 보내셨어요?",
            "집에 계실 때 주로 어느 방이나 자리에 계셨어요?",
            "집에 계시는 동안 보신 것이나 하신 일이 있으세요?",
        ],
        "ANCHOR": [
            "집에서 가장 오래 머문 자리가 어디였어요?",
            "집에 계실 때 손에 자주 잡았던 물건이 있으세요?",
            "집에서 보낸 시간을 떠올리면 어떤 모습이 먼저 생각나세요?",
        ],
    },
    "MEDIA": {
        "DEEPEN": [
            "티비나 방송은 언제쯤 보셨어요?",
            "어떤 방송이나 프로그램을 보셨어요?",
            "그 노래에서 가장 기억나는 부분이 있으세요?",
            "그 노래를 들을 때 기분은 어떠셨어요?",
            "그 방송에서 기억나는 내용이 있으세요?",
            "보실 때 어떤 장면이나 노래가 가장 기억나세요?",
            "그 방송에서 가장 먼저 떠오르는 사람이 있으세요?",
            "그걸 보실 때 기분은 어떠셨어요?",
        ],
        "ANCHOR": [
            "나중에 다시 떠올릴 만한 장면이나 노래가 있으세요?",
            "그 방송을 떠올리면 제일 먼저 생각나는 게 무엇인가요?",
            "그때 보신 내용 중에 기억나는 부분이 있으세요?",
        ],
    },
    "ACTIVITY": {
        "DEEPEN": [
            "그 일은 언제쯤 하셨어요?",
            "하실 때 기분은 어떠셨어요?",
            "그때 가장 먼저 기억나는 장면이 있으세요?",
        ],
        "ANCHOR": [
            "그 일은 주로 어디에서 하셨어요?",
            "그때는 주로 누구와 함께하셨어요?",
            "그 일을 하실 때 가장 기억나는 장면이 있으세요?",
        ],
    },
    "GAME": {
        "DEEPEN": [
            "어떤 게임을 하셨어요?",
            "게임하실 때 제일 기억나는 장면이 있으세요?",
            "그 게임은 혼자 하셨어요, 아니면 누군가와 같이 하셨어요?",
        ],
        "ANCHOR": [
            "오늘 하신 게임에서 가장 기억나는 장면이 있으세요?",
            "나중에 다시 떠올리면 그 게임의 어떤 부분이 먼저 생각날까요?",
            "게임하실 때 손으로 하신 동작 중 기억나는 게 있으세요?",
        ],
    },
    "REST": {
        "DEEPEN": [
            "그때는 어디에서 쉬고 계셨어요?",
            "쉬고 나서는 몸이 조금 괜찮으셨어요?",
            "쉬실 때 주변에서 들리거나 보였던 게 있으세요?",
        ],
        "ANCHOR": [
            "오늘 쉬었던 시간을 떠올리면 가장 먼저 생각나는 게 있으세요?",
            "낮잠이나 휴식 시간 중에 기억나는 장면이 있으세요?",
            "나중에 오늘 쉰 이야기를 한다면 어떤 말이 먼저 떠오를까요?",
        ],
    },
}


FUTURE_TOPIC_QUESTIONS = {
    "HEALTH": [
        "병원에는 언제쯤 가실 예정이세요?",
        "병원에는 혼자 가실 예정이세요, 함께 가실 분이 있으세요?",
    ],
    "PERSON": [
        "그분은 언제쯤 만나실 예정이세요?",
        "만나시면 어떤 이야기를 나누고 싶으세요?",
    ],
    "PLACE": [
        "그곳에는 언제쯤 가실 예정이세요?",
        "그곳에는 혼자 가실 예정이세요, 함께 가실 분이 있으세요?",
        "그곳에 가시면 무엇을 하실 예정이세요?",
        "그곳에는 어떻게 가실 예정이세요?",
    ],
    "SHOPPING": [
        "장 보러는 언제쯤 가실 예정이세요?",
        "이번에는 무엇을 사실 예정이세요?",
    ],
    "FOOD": [
        "그 음식은 언제쯤 드실 예정이세요?",
        "그 음식은 누구와 같이 드실 예정이세요?",
    ],
    "MEDIA": [
        "그 방송은 언제 보실 예정이세요?",
        "그 방송에서 어떤 내용이 가장 기대되세요?",
    ],
    "ACTIVITY": [
        "그 일은 언제쯤 하실 예정이세요?",
        "그 일은 혼자 하실 예정이세요?",
    ],
    "REST": [
        "언제쯤 쉬실 예정이세요?",
        "쉬실 때 어디에서 편하게 계실 예정이세요?",
    ],
}


WISH_TOPIC_QUESTIONS = {
    "PERSON": [
        "그분이 특히 보고 싶은 이유가 있으세요?",
        "그분을 만나면 가장 먼저 어떤 말을 하고 싶으세요?",
    ],
    "PLACE": [
        "그곳에 가고 싶은 이유가 있으세요?",
        "그곳에 가시면 가장 먼저 무엇을 하고 싶으세요?",
    ],
    "FOOD": [
        "어떤 음식이 가장 먼저 떠오르세요?",
        "그 음식이 생각난 이유가 있으세요?",
        "나중에 드신다면 누구와 같이 드시고 싶으세요?",
    ],
    "SHOPPING": [
        "그 물건을 사고 싶은 이유가 있으세요?",
        "그 물건은 어디에서 고르고 싶으세요?",
        "그 물건을 고를 때 가장 중요하게 보는 점이 있으세요?",
    ],
    "MEDIA": [
        "그 방송이나 내용을 보고 싶은 이유가 있으세요?",
        "보게 되면 어떤 부분이 가장 기대되세요?",
    ],
    "ACTIVITY": [
        "그 일을 하고 싶은 이유가 있으세요?",
        "하게 되면 누구와 함께하고 싶으세요?",
    ],
    "REST": [
        "어디에서 편하게 쉬고 싶으세요?",
        "쉬게 되면 무엇을 하며 보내고 싶으세요?",
    ],
}


TOPIC_CHANGE_ACKNOWLEDGEMENTS = [
    "아하, 그렇군요.",
    "그러셨군요.",
    "음, 그렇군요.",
]


RECALL_TRANSITION_ACKNOWLEDGEMENTS = [
    "아하, 그렇군요.",
    "그러셨군요.",
]


@timed_stage("session_read")
def _fetch_session_records(session_id: int) -> list[dict]:
    for attempt in range(2):
        try:
            resp = requests.get(
                f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
                timeout=10,
            )
            resp.raise_for_status()
            records = resp.json()
            if not isinstance(records, list) or any(not isinstance(record, dict) for record in records):
                raise ValueError("invalid_session_records_response")
            return records
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "세션 레코드 조회 재시도: sessionId=%s error=%s",
                    session_id,
                    e,
                )
                continue

            logger.error("세션 레코드 조회 실패: sessionId=%s, error=%s", session_id, e)

    raise DialogueStateUnavailable("session_records_unavailable")


@timed_stage("reply_save")
def _save_ai_reply(
    record_id: int,
    reply_text: str,
    episode_id: str | None = None,
    conversation_topic: str | None = None,
    conversation_stage: str | None = None,
    presented_recall_question_id: int | None = None,
):
    payload = {
        "recordId": record_id,
        "replyText": reply_text,
    }
    if episode_id:
        payload["episodeId"] = episode_id
    if conversation_topic:
        payload["conversationTopic"] = conversation_topic
    if conversation_stage:
        payload["conversationStage"] = conversation_stage
    if presented_recall_question_id is not None:
        payload["presentedRecallQuestionId"] = presented_recall_question_id

    for attempt in range(2):
        try:
            resp = requests.post(
                f"{SPRING_BASE_URL}/api/voice/reply",
                json=payload,
                timeout=10,
            )
            resp.raise_for_status()
            return
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "AI 답변 저장 재시도: recordId=%s error=%s",
                    record_id,
                    e,
                )
                continue

            raise


@timed_stage("recall_link")
def _link_recall_question(
    record_id: int,
    recall_question_id: int,
    answer_role: str,
) -> bool:
    payload = {
        "recordId": record_id,
        "recallQuestionId": recall_question_id,
        "answerRole": answer_role,
    }

    for attempt in range(2):
        try:
            resp = requests.post(
                f"{SPRING_BASE_URL}/api/voice/recall-link",
                json=payload,
                timeout=10,
            )
            resp.raise_for_status()

            logger.info(
                "레코드 질문 연결 완료: recordId=%s questionId=%s role=%s",
                record_id,
                recall_question_id,
                answer_role,
            )
            return True
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "레코드 질문 연결 재시도: recordId=%s error=%s",
                    record_id,
                    e,
                )
                continue

            logger.error(
                "레코드 질문 연결 실패: recordId=%s error=%s",
                record_id,
                e,
            )

    return False


def _create_recall_question(
    user_id: int,
    question_text: str,
    question_type: str,
    category: str,
    expected_answer: str,
) -> dict | None:
    body = {
        "userId": user_id,
        "questionText": question_text,
        "questionType": question_type,
        "category": category,
        "expectedAnswer": expected_answer,
    }

    resp = requests.post(
        f"{SPRING_BASE_URL}/api/recall/questions",
        json=body,
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()


@timed_stage("recall_questions_read")
def _fetch_recall_questions(user_id: int) -> list[dict]:
    for attempt in range(2):
        try:
            resp = requests.get(
                f"{SPRING_BASE_URL}/api/recall/questions/{user_id}",
                timeout=10,
            )
            resp.raise_for_status()
            questions = resp.json()
            if not isinstance(questions, list) or any(not isinstance(question, dict) for question in questions):
                raise ValueError("invalid_recall_question_response")
            return questions
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "회상 질문 조회 재시도: userId=%s error=%s",
                    user_id,
                    e,
                )
                continue

            logger.error("회상 질문 조회 실패: userId=%s error=%s", user_id, e)

    raise DialogueStateUnavailable("recall_questions_unavailable")


def _get_used_recall_clue_ids(questions: list[dict] | None) -> tuple[str, ...]:
    return tuple(
        dict.fromkeys(
            str(question.get("clueId") or "").strip()
            for question in questions or []
            if (
                str(question.get("questionType") or "").upper() == "RECALL"
                and str(question.get("clueId") or "").strip()
            )
        )
    )


def _find_or_create_fixed_question(user_id: int, question_data: dict) -> dict | None:
    questions = _fetch_recall_questions(user_id)
    target_text = question_data["questionText"].strip()

    for question in questions:
        question_text = str(question.get("questionText") or "").strip()
        category = str(question.get("category") or "").strip()

        if question_text == target_text and category == "INITIAL_FIXED":
            return question

    return _create_recall_question(
        user_id=user_id,
        question_text=question_data["questionText"],
        question_type=question_data["questionType"],
        category=question_data["category"],
        expected_answer=question_data["expectedAnswer"],
    )


def _link_fixed_question_answer(
    record_id: int,
    user_id: int,
    question_data: dict,
) -> bool:
    """고정 질문을 찾거나 만들고 현재 레코드에 FIXED로 연결한다."""
    fixed_question = _find_or_create_fixed_question(
        user_id=user_id,
        question_data=question_data,
    )
    fixed_question_id = fixed_question.get("questionId") if fixed_question else None

    if not fixed_question_id:
        logger.error("고정 질문 ID 확인 실패로 다음 단계 중단: recordId=%s", record_id)
        return False

    linked = _link_recall_question(
        record_id=record_id,
        recall_question_id=fixed_question_id,
        answer_role="FIXED",
    )

    if not linked:
        logger.error(
            "고정 질문 답변 연결 실패로 다음 단계 중단: recordId=%s questionId=%s",
            record_id,
            fixed_question_id,
        )
        return False

    return True


@timed_stage("orientation_status_read")
def _get_fixed_question_status(user_id: int) -> dict:
    """
    {"doneToday": bool, "onboardingDone": bool} 반환.
    onboardingDone=False면 아직 최초 5문항 온보딩 중, True면 이후 하루 1문항 모드.
    """
    for attempt in range(2):
        try:
            resp = requests.get(
                f"{SPRING_BASE_URL}/api/voice/fixed-status/{user_id}",
                timeout=10,
            )
            resp.raise_for_status()
            data = resp.json().get("data") or {}
            if any(type(data.get(field)) is not bool for field in ("doneToday", "onboardingDone")):
                raise ValueError("invalid_orientation_status_response")
            return {
                "doneToday": bool(data.get("doneToday")),
                "onboardingDone": bool(data.get("onboardingDone")),
            }
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "고정질문 상태 조회 재시도: userId=%s error=%s",
                    user_id,
                    e,
                )
                continue

            logger.error("고정질문 상태 조회 실패: userId=%s error=%s", user_id, e)

    raise DialogueStateUnavailable("orientation_status_unavailable")


def _mark_fixed_questions_done_today(user_id: int, onboarding: bool = False) -> bool:
    for attempt in range(2):
        try:
            resp = requests.post(
                f"{SPRING_BASE_URL}/api/voice/fixed-complete/{user_id}",
                params={"onboarding": str(onboarding).lower()},
                timeout=10,
            )
            resp.raise_for_status()
            return True
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "고정질문 완료 기록 재시도: userId=%s error=%s",
                    user_id,
                    e,
                )
                continue

            logger.error("고정질문 완료 기록 실패: userId=%s error=%s", user_id, e)

    return False


def _pick_daily_fixed_question_index(user_id: int, on_date: date | None = None) -> int:
    """
    userId + 날짜를 시드로 결정론적 인덱스를 뽑는다.
    같은 유저가 같은 날 여러 번 호출해도(세션 시작 인사말 / 실제 답변 채점) 항상 같은 질문을 가리켜야
    인사말에서 말한 질문과 실제로 채점하는 질문이 어긋나지 않는다.
    """
    on_date = on_date or date.today()
    seed = f"{user_id}:{on_date.isoformat()}"
    digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()
    return int(digest, 16) % len(FIXED_QUESTIONS)


def _count_fixed_answers(session_records: list[dict]) -> int:
    return sum(
        1
        for record in session_records
        if str(record.get("answerRole") or "").upper() == "FIXED"
    )


def _extract_recall_candidate_records(session_records: list[dict]) -> list[dict]:
    """
    가장 최근 회상 사이클 이후의 자유대화 답변만 회상 질문 후보로 사용한다.

    제외 대상:
    - 초기 고정 질문이 끝나기 전 첫 인사/답변
    - FIXED: 초기 고정 질문 답변
    - INITIAL: 이미 회상 질문의 기준 답변으로 사용된 답변
    - RECALL: 회상 질문에 대한 답변

    INITIAL과 RECALL을 제외하지 않으면
    회상 질문 → 회상 답변 → 다시 회상 질문 후보
    형태의 순환 구조가 생길 수 있다.
    """
    candidates = []
    sorted_records = ordered_records(session_records)

    boundary_position = -1
    fixed_count = 0

    for position, record in enumerate(sorted_records):
        role = str(record.get("answerRole") or "").upper()

        if role == "FIXED":
            fixed_count += 1
            boundary_position = position
            continue

        if role in {"INITIAL", "RECALL"}:
            boundary_position = position

    # 고정 질문이 0개인 세션은 오늘 고정 질문을 이미 마친 뒤 다시 시작한
    # 자유대화 세션이다. 온보딩 이후에는 하루 1개만 노출되므로 1개는 항상
    # "오늘의 고정 질문을 마치고 자유대화로 넘어간" 정상 상태다.
    # 2~4개가 있으면(온보딩 5개 순차 진행 중) 아직 고정 질문이 끝나지 않은 것이다.
    if 1 < fixed_count < len(FIXED_QUESTIONS):
        return []

    for position, record in enumerate(sorted_records):
        role = str(record.get("answerRole") or "").upper()
        text = str(record.get("transcriptText") or "").strip()

        if position <= boundary_position:
            continue

        if not text:
            continue

        if is_repeat_reminder(text) or short_reference(text) or declines_detail(text):
            continue

        if record.get("conversationStage") in {"PAUSED", "RECALL_SKIPPED", "RECALL_CLARIFY", "TOPIC_SKIPPED", "CLARIFY"}:
            continue

        if role in {"FIXED", "INITIAL", "RECALL"}:
            continue

        candidates.append(record)

    return candidates


def _extract_recall_candidate_transcripts(session_records: list[dict]) -> list[str]:
    return [
        str(record.get("transcriptText") or "").strip()
        for record in _extract_recall_candidate_records(session_records)
    ]


def _normalize_memory_event_topic(topic: str | None) -> str:
    normalized = str(topic or "").strip().upper()
    aliases = {
        "FOOD": "MEAL",
        "HOME": "PLACE",
        "SCENERY": "PLACE",
        "REST": "ACTIVITY",
        "ROUTINE": "ACTIVITY",
        "GAME": "ACTIVITY",
        "SHOPPING": "OBJECT",
    }

    if normalized in {"", "LOW_INFO", "NEGATIVE"}:
        return "UNGROUPED"

    return aliases.get(normalized, normalized)


def _fallback_answer_type_for_topic(topic: str) -> MemoryAnswerType:
    topic_map = {
        "MEAL": MemoryAnswerType.FOOD,
        "PERSON": MemoryAnswerType.PERSON,
        "PLACE": MemoryAnswerType.PLACE,
        "MEDIA": MemoryAnswerType.MEDIA,
        "ACTIVITY": MemoryAnswerType.ACTIVITY,
        "OBJECT": MemoryAnswerType.OBJECT,
    }
    return topic_map.get(topic, MemoryAnswerType.UNKNOWN)


EXPECTED_EPISODE_DETAIL_TYPES_BY_TOPIC = {
    "ACTIVITY": ("ACTIVITY", "TIME", "PLACE", "PERSON"),
    "HEALTH": ("TIME", "PLACE", "PERSON"),
    "MEAL": ("FOOD", "TIME", "PLACE", "PERSON"),
    "MEDIA": ("MEDIA", "TIME", "PLACE", "PERSON"),
    "PLACE": ("PLACE", "TIME", "PERSON"),
    "PERSON": ("PERSON", "TIME", "PLACE"),
    "OBJECT": ("OBJECT", "TIME", "PLACE", "PERSON"),
    "REST": ("TIME", "PLACE"),
}

GENERIC_EPISODE_DETAIL_VALUES = {
    "PLACE": set(CONTEXT_ONLY_PLACES),
    "ACTIVITY": {"게임", "오락", "일", "활동"},
    "MEDIA": {"방송", "프로그램", "노래", "영상", "티비", "텔레비전"},
    "FOOD": {"음식", "밥", "간식"},
    "OBJECT": {"물건"},
}

MISSING_DETAIL_PRIORITIZED_TOPICS = {
    "FOOD",
    "HEALTH",
    "ACTIVITY",
    "GAME",
    "MEDIA",
    "PERSON",
    "PLACE",
    "OBJECT",
    "REST",
}


EPISODE_DETAIL_QUESTION_CANDIDATES_BY_TOPIC = {
    "HEALTH": {
        "TIME": ["병원에는 언제쯤 다녀오셨어요?", "약은 언제쯤 드셨어요?"],
        "PLACE": ["어느 병원에 다녀오셨어요?"],
        "PERSON": ["병원에는 누구와 함께 다녀오셨어요?"],
    },
    "MEAL": {
        "FOOD": ["무엇을 드셨어요?"],
        "TIME": ["그 음식은 언제쯤 드셨어요?"],
        "PLACE": ["그 음식은 어디에서 드셨어요?"],
        "PERSON": ["그때 누구와 같이 드셨어요?"],
    },
    "MEDIA": {
        "MEDIA": ["어떤 방송이나 프로그램을 보셨어요?", "어떤 노래를 들으셨어요?"],
        "TIME": ["티비나 방송은 언제쯤 보셨어요?"],
        "PLACE": ["그때는 어디에서 보고 계셨어요?"],
        "PERSON": ["그걸 보실 때 같이 계신 분이 있으셨어요?"],
    },
    "PLACE": {
        "PLACE": ["어디에 다녀오셨어요?"],
        "TIME": ["그곳에는 언제쯤 다녀오셨어요?"],
        "PERSON": ["그곳에는 누구와 함께 다녀오셨어요?"],
    },
    "PERSON": {
        "PERSON": ["누구와 이야기하셨어요?"],
        "TIME": ["그분과는 언제쯤 이야기하셨어요?"],
        "PLACE": ["그분과는 어디에서 이야기하셨어요?"],
    },
    "ACTIVITY": {
        "ACTIVITY": ["무엇을 하셨어요?"],
        "TIME": ["그 일은 언제쯤 하셨어요?"],
        "PLACE": ["그 일은 어디에서 하셨어요?"],
        "PERSON": ["그 일은 누구와 함께하셨어요?"],
    },
    "GAME": {
        "ACTIVITY": ["어떤 게임을 하셨어요?"],
        "TIME": ["그 게임은 언제쯤 하셨어요?"],
        "PLACE": ["그 게임은 어디에서 하셨어요?"],
        "PERSON": ["그 게임은 누구와 함께하셨어요?"],
    },
    "OBJECT": {
        "OBJECT": ["어떤 물건을 사용하셨어요?"],
        "TIME": ["그 물건은 언제 사용하셨어요?"],
        "PLACE": ["그 물건은 어디에 두셨어요?"],
        "PERSON": ["그 물건은 누구와 같이 사용하셨어요?"],
    },
    "REST": {
        "TIME": ["언제쯤 쉬고 계셨어요?"],
        "PLACE": ["어디에서 쉬고 계셨어요?"],
    },
}


def _merge_memory_clues(*clue_groups: tuple[dict[str, str], ...]) -> tuple[dict[str, str], ...]:
    merged = []

    for clues in clue_groups:
        for clue in clues:
            normalized = {
                "answerType": str(clue.get("answerType") or "").strip().upper(),
                "answerValue": " ".join(str(clue.get("answerValue") or "").split()),
            }

            if normalized["answerType"] and normalized["answerValue"] and normalized not in merged:
                merged.append(normalized)

    return tuple(merged)


def _get_missing_episode_answer_types(
    topic: str | None,
    cycle_texts: list[str],
    latest_text: str,
) -> tuple[str, ...]:
    normalized_topic = _normalize_memory_event_topic(topic)
    expected = EXPECTED_EPISODE_DETAIL_TYPES_BY_TOPIC.get(normalized_topic, ())

    if not expected:
        return ()

    state = build_followup_intent_state(
        None, _build_generation_history(cycle_texts, latest_text),
        _fallback_answer_type_for_topic(normalized_topic), GENERIC_EPISODE_DETAIL_VALUES,
    )
    return tuple(answer_type for answer_type in expected if answer_type not in state.known_types)


def _get_episode_detail_question_candidates(
    topic: str | None,
    missing_answer_types: tuple[str, ...],
    latest_text: str,
    cycle_texts: list[str],
) -> list[str]:
    raw_topic = str(topic or "").strip().upper()
    normalized_topic = _normalize_memory_event_topic(topic)
    topic_questions = (
        EPISODE_DETAIL_QUESTION_CANDIDATES_BY_TOPIC.get(raw_topic)
        or EPISODE_DETAIL_QUESTION_CANDIDATES_BY_TOPIC.get(normalized_topic, {})
    )
    candidates = []

    for answer_type in missing_answer_types:
        candidates.extend(topic_questions.get(answer_type, []))

    if not candidates:
        return []

    context = " ".join([latest_text, *cycle_texts])

    if normalized_topic == "HEALTH":
        if not _has_medicine_reference(context):
            candidates = [
                question
                for question in candidates
                if "약" not in question
            ]

        if not _has_confirmed_reference([latest_text, *cycle_texts], "병원"):
            candidates = [
                question
                for question in candidates
                if "병원" not in question
            ]

    if _contains_any(context, ("집에 있었", "집에서", "집에만", "안 나갔", "나가지 않았")):
        candidates = [
            question
            for question in candidates
            if "다녀오" not in question and "그곳에는" not in question
        ]

    if _contains_any(context, ("혼자",)):
        candidates = [
            question
            for question in candidates
            if "누구와 함께" not in question and "같이 계신 분" not in question
        ]

    if _contains_any(
        context,
        ("아침", "점심", "저녁", "오전", "오후", "새벽", "밤에", "낮에"),
    ) or re.search(r"\d+\s*시", context):
        candidates = [
            question
            for question in candidates
            if "언제" not in question
        ]

    return candidates


_MEMORY_FOLLOWUP_REFERENCE_CUES = (
    "그때",
    "그곳",
    "같이",
    "함께",
    "그 음식",
    "그 방송",
    "그 일",
    "그 물건",
    "그 선물",
    "무슨 선물",
    "어떤 선물",
)
_MEMORY_TOPIC_TRANSITION_CUES = (
    "그 뒤",
    "그 다음",
    "다른 이야기",
    "이번에는",
    "이어서",
    "요즘",
    "평소",
    "최근에는",
)

_MEMORY_EVENT_REFERENCE_TOPICS = (
    ("그 음식", "MEAL"),
    ("그 방송", "MEDIA"),
    ("그 물건", "OBJECT"),
    ("그 선물", "OBJECT"),
)

_MEMORY_EVENT_SEQUENCE_PATTERN = re.compile(
    r"(?:고\s+나서|(?:한|은|는|난)\s*뒤|그\s*뒤|그\s*다음|그\s*후|이후(?:에는)?|그리고)"
)


def _detect_memory_event_reference_topic(question: str) -> str | None:
    normalized = " ".join(str(question or "").split())

    for cue, topic in _MEMORY_EVENT_REFERENCE_TOPICS:
        if cue in normalized:
            return topic

    return None


def _is_referential_memory_followup(question: str) -> bool:
    normalized = " ".join(str(question or "").split())

    if any(cue in normalized for cue in _MEMORY_TOPIC_TRANSITION_CUES):
        return False

    if any(cue in normalized for cue in _MEMORY_FOLLOWUP_REFERENCE_CUES):
        return True

    tokens = _tokenize_cached(normalized)
    return any(
        token.form == "그"
        and token.tag == "MM"
        and index + 1 < len(tokens)
        and tokens[index + 1].tag.startswith("NN")
        for index, token in enumerate(tokens)
    )


def _is_memory_topic_transition(question: str) -> bool:
    normalized = " ".join(str(question or "").split())
    return any(cue in normalized for cue in _MEMORY_TOPIC_TRANSITION_CUES)


def _extract_sequential_event_clues(text: str) -> tuple[dict, ...]:
    clauses = [
        clause.strip(" ,.!?")
        for clause in _MEMORY_EVENT_SEQUENCE_PATTERN.split(str(text or ""))
        if clause.strip(" ,.!?")
    ]

    if len(clauses) < 2:
        return ()

    final_clues = extract_memory_clues(
        clauses[-1],
        MemoryAnswerType.UNKNOWN,
    )
    return tuple(final_clues)


def _build_memory_evidence_payloads(
    session_records: list[dict],
) -> list[dict]:
    event_frames = resolve_event_frames(session_records)
    candidate_records = _extract_recall_candidate_records(session_records)
    candidate_ids = {
        int(record.get("recordId") or 0)
        for record in candidate_records
    }
    sorted_records = sorted(
        session_records,
        key=lambda record: int(record.get("turnOrder") or record.get("recordId") or 0),
    )
    cycle_texts = []
    payloads = []
    last_event_topic = None
    last_event_clue_values: set[str] = set()
    segment_by_record = {
        int(row.get("recordId") or 0): str(segment[0].get("recordId"))
        for segment in episode_segments(sorted_records)
        for row in segment
    }
    previous_segment = None

    for index, record in enumerate(sorted_records):
        record_id = int(record.get("recordId") or 0)

        if record_id not in candidate_ids:
            continue

        frame = event_frames.get(record_id, EventFrame())
        if not frame.allows_past_evidence:
            continue

        segment_id = segment_by_record.get(record_id, "")
        if segment_id != previous_segment:
            cycle_texts = []
            last_event_topic = None
            last_event_clue_values = set()
        previous_segment = segment_id

        text = str(record.get("transcriptText") or "").strip()
        previous_question = (
            str(sorted_records[index - 1].get("aiReplyText") or "")
            if index > 0
            else ""
        )
        cycle_texts.append(text)
        detected_topic = _normalize_memory_event_topic(
            _detect_conversation_topic(
                text,
                cycle_texts,
                previous_question,
            )
        )
        general_clues = extract_memory_clues(text)
        if detected_topic == "UNGROUPED" and extract_action_object(text):
            detected_topic = "OBJECT"
        elif detected_topic in {"UNGROUPED", "PLACE"} and any(
            clue["answerType"] == "ACTIVITY" for clue in general_clues
        ):
            detected_topic = "ACTIVITY"
        sequential_event_clues = _extract_sequential_event_clues(text)
        sequential_event_topic = (
            _normalize_memory_event_topic(sequential_event_clues[-1]["answerType"])
            if sequential_event_clues
            else None
        )
        if sequential_event_topic not in {None, "UNGROUPED"}:
            detected_topic = sequential_event_topic
        question_topics = {
            _normalize_memory_event_topic(_detect_topic_in_text(previous_question)),
            _normalize_memory_event_topic(_detect_topic_from_question(previous_question)),
        }
        question_context_topic = _normalize_memory_event_topic(
            _detect_topic_from_question(previous_question)
        )
        question_answer_type = infer_answer_type(previous_question)
        question_clues = extract_memory_clues(text, question_answer_type)
        has_question_answer = (
            question_answer_type
            not in {
                MemoryAnswerType.UNKNOWN,
                MemoryAnswerType.ACTIVITY,
            }
            and any(
                clue["answerType"] == question_answer_type.value
                for clue in question_clues
            )
        )
        is_correction = is_correction_utterance(text)
        referenced_event_topic = _detect_memory_event_reference_topic(
            previous_question
        )
        references_previous_clue = any(
            _keyword_occurs(value, previous_question)
            for value in last_event_clue_values
        )
        question_actions = extract_memory_action_concepts(previous_question)
        answer_actions = extract_memory_action_concepts(text)
        has_topic_opening_scope = _contains_any(
            previous_question,
            ("오늘", "요즘", "최근", "평소", "이번에는"),
        )
        expected_topic_answer_type = _fallback_answer_type_for_topic(
            question_context_topic
        )
        question_starts_new_topic = bool(
            last_event_topic not in {None, "UNGROUPED"}
            and question_context_topic not in {None, "UNGROUPED"}
            and question_context_topic != last_event_topic
            and (
                question_answer_type == expected_topic_answer_type
                or (
                    question_answer_type == MemoryAnswerType.UNKNOWN
                    and has_topic_opening_scope
                )
            )
            and referenced_event_topic is None
            and (
                (
                    not _is_referential_memory_followup(previous_question)
                    and not references_previous_clue
                )
                or has_topic_opening_scope
            )
        )
        answers_previous_action = bool(
            last_event_topic
            and not question_starts_new_topic
            and referenced_event_topic in {None, last_event_topic}
            and not has_topic_opening_scope
            and question_answer_type in {
                MemoryAnswerType.TIME, MemoryAnswerType.PLACE, MemoryAnswerType.PERSON,
            }
            and question_actions.intersection(answer_actions)
        )
        if answers_previous_action:
            detected_topic = last_event_topic
            question_topics.add(last_event_topic)
        continues_previous_event = should_continue_memory_event(
            last_event_topic=last_event_topic,
            detected_topic=detected_topic,
            question_topics=question_topics,
            answer_type=question_answer_type,
            is_correction=is_correction,
            is_referential_followup=(
                (
                    _is_referential_memory_followup(previous_question)
                    or references_previous_clue
                    or answers_previous_action
                )
                and referenced_event_topic in {None, last_event_topic}
            ),
            has_conflicting_action=bool(
                question_actions
                and answer_actions
                and question_actions.isdisjoint(answer_actions)
            ),
            has_confirmed_clue=bool(question_clues),
            is_topic_transition=(
                _is_memory_topic_transition(previous_question)
                or question_starts_new_topic
                or (
                    sequential_event_topic not in {None, "UNGROUPED"}
                    and sequential_event_topic != last_event_topic
                )
            ),
        )
        if (last_event_topic and referenced_event_topic in {None, last_event_topic}
                and answers_event_detail(text, previous_question, cycle_texts[:-1])):
            continues_previous_event = True
        event_topic = (
            last_event_topic
            if continues_previous_event
            else (
                sequential_event_topic
                or referenced_event_topic
                or (
                    question_context_topic
                    if (
                        has_question_answer
                        and question_context_topic != "UNGROUPED"
                    )
                    else detected_topic
                )
            )
        )

        event_answer_type = _fallback_answer_type_for_topic(event_topic)
        if sequential_event_topic not in {None, "UNGROUPED"}:
            answer_type = event_answer_type
        elif (
            question_answer_type != MemoryAnswerType.UNKNOWN
            and (
                continues_previous_event
                or has_question_answer
                or event_topic in question_topics
            )
        ):
            answer_type = question_answer_type
        elif (
            not continues_previous_event
            and event_answer_type != MemoryAnswerType.UNKNOWN
        ):
            answer_type = event_answer_type
        else:
            answer_type = event_answer_type

        clues = (
            list(sequential_event_clues)
            if sequential_event_clues
            else extract_memory_clues(text, answer_type)
        )
        answer_value = next(
            (
                clue["answerValue"]
                for clue in clues
                if clue["answerType"] == answer_type.value
            ),
            "",
        )

        evaluation = score_recall_memory_candidate(text)
        payloads.append(
            {
                "sourceRecordId": record_id,
                "episodeId": segment_id,
                "turnOrder": int(record.get("turnOrder") or 0) or None,
                "sourceText": text,
                "topic": event_topic,
                "answerType": answer_type.value,
                "answerValue": answer_value,
                "clues": list(clues),
                "qualityScore": evaluation["recallScore"],
                "continuesPreviousEvent": continues_previous_event,
                "isCorrection": is_correction,
            }
        )
        current_clue_values = {
            clue["answerValue"]
            for clue in clues
            if clue.get("answerValue")
        }
        if continues_previous_event:
            last_event_clue_values.update(current_clue_values)
        else:
            last_event_clue_values = current_clue_values
        last_event_topic = event_topic

    return payloads


def _is_episodic_recall_text(text: str) -> bool:
    evaluation = score_recall_memory_candidate(text)
    episodic_reasons = {
        "has_past_tense",
        "has_past_action",
        "has_general_past_action",
    }
    return bool(
        evaluation["isValid"]
        and episodic_reasons.intersection(evaluation.get("reasons") or ())
    )


@timed_stage("memory_selection")
def _get_structured_recall_ready_context(
    session_records: list[dict],
    previous_question: str = "",
    used_clue_ids: tuple[str, ...] = (),
) -> RecallCandidateContext:
    payloads = _build_memory_evidence_payloads(session_records)

    records = ordered_records(session_records)
    if not payloads or not records:
        return RecallCandidateContext()

    latest_record_id = int(records[-1].get("recordId") or 0)
    latest_payload = next((item for item in payloads if item["sourceRecordId"] == latest_record_id), {})
    latest_text = str(records[-1].get("transcriptText") or "")
    prior_payloads = [item for item in payloads if item["sourceRecordId"] != latest_record_id]
    transcripts = [payload["sourceText"] for payload in payloads]
    latest_topic = _detect_conversation_topic(
        latest_text,
        transcripts,
        previous_question,
    )
    active_topic = latest_topic

    if active_topic in {None, "LOW_INFO", "NEGATIVE"}:
        active_topic = _detect_topic_from_question(previous_question)

    normalized_active_topic = _normalize_memory_event_topic(active_topic)
    matured_payloads = [
        payload
        for payload in prior_payloads
        if (
            normalized_active_topic == "UNGROUPED"
            or _normalize_memory_event_topic(payload.get("topic"))
            != normalized_active_topic
        )
    ]
    if any(row.get("episodeId") for row in session_records):
        active = active_episode_records(records)
        active_id = str(active[0].get("recordId")) if active else None
        active_size = len(active)
        matured_payloads = [
            item for item in prior_payloads
            if item.get("episodeId") != active_id or active_size >= MAX_CONSECUTIVE_TOPIC_TURNS
        ]
    selection = build_memory_candidate_selection(
        matured_payloads,
        used_clue_ids=used_clue_ids,
        max_candidates=3,
    )
    episodic_record_ids = {
        int(payload.get("sourceRecordId") or 0)
        for payload in matured_payloads
        if _is_episodic_recall_text(payload.get("sourceText") or "")
    }
    eligible_candidates = tuple(
        candidate
        for candidate in selection.candidates
        if episodic_record_ids.intersection(candidate.source_record_ids)
    )
    wait_turns = max(
        (memory_wait_turns(records, candidate.source_record_ids)
         for candidate in eligible_candidates), default=0,
    )
    candidate_wait_seconds = [memory_wait_seconds(records, candidate.source_record_ids)
                              for candidate in eligible_candidates]
    oldest_wait_seconds = max((value for value in candidate_wait_seconds if value is not None), default=None)
    timing = decide_recall_timing(
        matured_candidate_count=len(eligible_candidates),
        memory_wait_turns=wait_turns,
        memory_wait_seconds=oldest_wait_seconds,
        latest_is_memory_candidate=_is_episodic_recall_text(latest_text),
        latest_has_followup_context=(
            latest_topic is not None
            and latest_topic not in {"LOW_INFO", "NEGATIVE"}
        ),
        latest_is_low_info=_is_low_info_response(latest_text),
        latest_is_negative=_is_negative_response(latest_text),
        latest_continues_event=bool(
            latest_payload.get("continuesPreviousEvent")
        ),
    )

    logger.info(
        "recall_timing action=%s reason=%s eligible_count=%s memory_wait_turns=%s memory_wait_seconds=%s",
        timing.action.value, timing.reason, len(eligible_candidates), wait_turns, oldest_wait_seconds,
    )
    if timing.action != RecallTimingAction.ASK_RECALL:
        return RecallCandidateContext()

    selected_clues = tuple(
        (candidate, candidate.select_recall_clue(used_clue_ids))
        for candidate in eligible_candidates
    )
    serialized_by_event_id = {
        candidate["eventId"]: candidate
        for candidate in selection.to_dict()["candidates"]
    }
    return RecallCandidateContext(
        prior_wait_seconds=(min(candidate_wait_seconds)
                            if candidate_wait_seconds and all(value is not None for value in candidate_wait_seconds)
                            else None),
        prior_wait_turns=min(
            (memory_wait_turns(records, candidate.source_record_ids)
             for candidate in eligible_candidates), default=0,
        ),
        conversation_history=tuple(
            selected_clue.source_text
            for _candidate, selected_clue in selected_clues
        ),
        source_record_ids=tuple(
            (selected_clue.source_text, selected_clue.source_record_id)
            for _candidate, selected_clue in selected_clues
        ),
        memory_candidates=tuple(
            {
                **serialized_by_event_id[candidate.event_id],
                "episodeId": next((row.get("episodeId") for row in session_records
                                   if row.get("recordId") == selected_clue.source_record_id), None),
                "eventId": candidate.event_id,
                "sourceRecordId": selected_clue.source_record_id,
                "sourceText": selected_clue.source_text,
                "answerType": selected_clue.clue.answer_type.value,
                "answerValue": selected_clue.clue.answer_value,
            }
            for candidate, selected_clue in selected_clues
        ),
    )


def _get_recall_ready_history(
    transcripts: list[str],
    previous_question: str = "",
) -> list[str]:
    """
    충분히 축적되고 한 턴 이상 지난 기억 단서만 회상 질문 생성에 사용한다.

    최신 답변을 즉시 다시 묻지 않도록 제외한 뒤, 그 이전 대화에서
    서로 다른 유효한 memoryPoint 후보가 2개 이상이어야 하며,
    최신 답변의 주제가 더 이어져야 한다면 회상 전환을 잠시 미룬다.
    """
    if len(transcripts) < 2:
        return []

    matured_history = transcripts[:-1]
    matured_candidates = select_recall_memory_candidates(matured_history)

    latest_text = transcripts[-1]
    latest_evaluation = score_recall_memory_candidate(latest_text)
    latest_topic = _detect_conversation_topic(
        latest_text,
        transcripts,
        previous_question,
    )
    timing = decide_recall_timing(
        matured_candidate_count=len(matured_candidates),
        latest_is_memory_candidate=bool(latest_evaluation["isValid"]),
        latest_has_followup_context=(
            latest_topic is not None
            and latest_topic not in {"LOW_INFO", "NEGATIVE"}
        ),
        latest_is_low_info=_is_low_info_response(latest_text),
        latest_is_negative=_is_negative_response(latest_text),
    )

    if timing.action != RecallTimingAction.ASK_RECALL:
        return []

    return matured_history


def _find_memory_source_record_id(
    session_records: list[dict],
    source_text: str,
) -> int | None:
    normalized_source = _normalize_text(source_text)

    if not normalized_source:
        return None

    candidate_record_ids = []

    for record in session_records:
        record_text = _normalize_text(record.get("transcriptText") or "")
        role = str(record.get("answerRole") or "").upper()

        if record_text != normalized_source:
            continue

        if role in {"FIXED", "INITIAL", "RECALL"}:
            continue

        record_id = record.get("recordId")

        if record_id is not None:
            candidate_record_ids.append(int(record_id))

    return max(candidate_record_ids) if candidate_record_ids else None


def _normalize_question_text(text: str) -> str:
    return " ".join(str(text or "").split())


def _find_recall_question_text(user_id: int, question_id: int) -> str:
    for question in _fetch_recall_questions(user_id):
        current_question_id = question.get("questionId")

        if current_question_id is None or int(current_question_id) != int(question_id):
            continue

        return _normalize_question_text(question.get("questionText") or "")

    return ""


def _was_recall_question_presented(
    session_records: list[dict],
    current_record_id: int,
    recall_question_id: int,
    question_text: str,
) -> bool:
    records = ordered_records(session_records)
    current_index = next(
        (i for i, row in enumerate(records) if int(row.get("recordId") or 0) == int(current_record_id)),
        0,
    )
    if current_index <= 0:
        return False
    return presented_question_matches(records[current_index - 1], recall_question_id, question_text)


def _count_completed_recall_answers(session_records: list[dict] | None) -> int:
    return sum(
        1
        for record in (session_records or [])
        if str(record.get("answerRole") or "").upper() == "RECALL"
    )


def _normalize_text(text: str) -> str:
    return " ".join(str(text or "").split())


def _get_recent_ai_replies(session_records: list[dict] | None) -> set[str]:
    replies = set()

    for record in session_records or []:
        reply_text = _normalize_text(record.get("aiReplyText") or "")

        if reply_text:
            replies.add(reply_text)

    return replies


def _get_recent_ai_reply_list(session_records: list[dict] | None) -> list[str]:
    replies = []

    for record in session_records or []:
        reply_text = _normalize_text(record.get("aiReplyText") or "")

        if reply_text:
            replies.append(reply_text)

    return replies


def _get_recent_transcript_list(
    session_records: list[dict] | None,
    limit: int = 8,
) -> list[str]:
    transcripts = []

    for record in session_records or []:
        transcript_text = _normalize_text(record.get("transcriptText") or "")

        if transcript_text:
            transcripts.append(transcript_text)

    return transcripts[-limit:]


def _get_cycle_transcripts(session_records: list[dict] | None) -> list[str]:
    if not session_records:
        return []

    return _extract_recall_candidate_transcripts(session_records)


def _build_generation_history(cycle_texts: list[str], latest_text: str) -> list[str]:
    history = [text for text in cycle_texts if _normalize_text(text)]
    latest_text = _normalize_text(latest_text)

    if latest_text and (not history or _normalize_text(history[-1]) != latest_text):
        history.append(latest_text)

    return history


_kiwi = Kiwi()

# 키워드 리스트의 각 항목은 "드셨"/"다녀" 같은 활용 조각이 아니라
# 사전형 어간("드시"/"다녀오")으로 관리한다. 조각을 혼자 떼어 분석하면
# 형태소 분석기도 헷갈리지만("다녀"만 넣으면 "다니다"로 오분석),
# 실제 문장 속에서는 정확한 어간이 나오기 때문이다("다녀왔어" 안에서는
# 정확히 "다녀오"). 그래서 매칭은 부분 문자열이 아니라 완전히
# 형태소(토큰) 단위로만 한다.
_NOUN_TAGS = {"NNG", "NNP", "NNB", "NR", "NP"}
_EXACT_MATCH_TAGS = _NOUN_TAGS | {"MAG", "XR", "SL", "SH", "SN", "IC"}
# 동사/형용사는 접두사로 비교한다. "듣다/눕다/어렵다/덥다/춥다" 같은
# 불규칙 활용 어간은 kiwi가 "VV-I"/"VA-I"처럼 -I가 붙은 태그를 쓴다.
_PREDICATE_TAG_PREFIXES = ("VV", "VA", "VX", "XSA", "XSV")
# 어미류: 용언 뒤에 붙는 시제/문체/연결 꼬리표. 부정 표현의 끝 위치를
# 찾을 때 이 태그가 이어지는 동안은 같은 용언구의 일부로 본다.
_INFLECTION_TAGS = {"EP", "EF", "EC", "ETN", "ETM"}
_tokenize_cache: dict[str, tuple] = {}


def _is_match_tag(tag: str) -> bool:
    return tag in _EXACT_MATCH_TAGS or tag.startswith(_PREDICATE_TAG_PREFIXES)


def _tokenize_cached(text: str) -> tuple:
    text = str(text or "")
    cached = _tokenize_cache.get(text)

    if cached is not None:
        return cached

    tokens = tuple(_kiwi.tokenize(text))

    if len(_tokenize_cache) > 2000:
        _tokenize_cache.clear()

    _tokenize_cache[text] = tokens
    return tokens


def _match_token_forms(text: str) -> list[str]:
    return [token.form for token in _tokenize_cached(text) if _is_match_tag(token.tag)]


def _full_token_forms(text: str) -> list[str]:
    return [token.form for token in _tokenize_cached(text)]


def _is_contiguous_subsequence(needle: list[str], haystack: list[str]) -> bool:
    if not needle:
        return False

    span = len(needle)

    return any(
        haystack[start:start + span] == needle
        for start in range(len(haystack) - span + 1)
    )


# 조사(격조사/보조사/접속조사)는 의미를 가른다("집에만" vs "집을").
# 어미(EP/EF/EC 등 활용형 꼬리)는 그냥 시제/문체 차이라 무시해도 된다.
_PARTICLE_TAG_PREFIXES = ("JK", "JX", "JC")


def _keyword_has_meaningful_particle(word: str) -> bool:
    return any(
        token.tag.startswith(_PARTICLE_TAG_PREFIXES)
        for token in _tokenize_cached(word)
    )


def _keyword_occurs(keyword: str, text: str, strict: bool = False) -> bool:
    """strict=True는 어미(시제/부정 등)까지 그대로 지켜야 하는 키워드용이다.
    예: "안 좋"은 "안 좋아요"에는 맞아도 "좋아요"에는 맞으면 안 된다.
    """
    keyword_content_forms = _match_token_forms(keyword)

    if not keyword_content_forms:
        return False

    if (
        not strict
        and len(keyword_content_forms) == 1
        and not _keyword_has_meaningful_particle(keyword)
    ):
        # 단일 형태소 키워드("드셨"→"드시", "형")는 조사/어미와 무관하게
        # 문장 안에 그 형태소가 등장하는지만 본다(활용형에 안정적).
        return keyword_content_forms[0] in _match_token_forms(text)

    # 조사·어미가 의미를 가르는 구 키워드("집에만", "안 좋")는 그걸 빼면
    # 의미가 사라지거나("집에만"→"집") 반대 뜻이 될 수 있어서("안 좋"→"좋")
    # 조사·어미를 포함한 전체 토큰 나열이 연속으로 등장하는지 확인한다.
    return _is_contiguous_subsequence(
        _full_token_forms(keyword),
        _full_token_forms(text),
    )


def _contains_any(text: str, keywords: tuple[str, ...], strict: bool = False) -> bool:
    return any(_keyword_occurs(keyword, text, strict=strict) for keyword in keywords)


def _get_final_correction_segment(text: str) -> str:
    text = str(text or "")
    correction_patterns = (
        r"다시\s*생각해\s*보니",
        r"정정(?:할게요?|하면)",
        r"(?:^|[,.;!?]\s*|\s+)(?:아니에요|아니요|아니)(?![가-힣])\s*[,，]?\s*",
    )
    last_end = -1

    for pattern in correction_patterns:
        for match in re.finditer(pattern, text):
            last_end = max(last_end, match.end())

    corrected_text = text if last_end < 0 else text[last_end:].strip()
    alternative_matches = list(
        re.finditer(r"(?:아니라|아니고|말고)\s*", corrected_text)
    )

    if alternative_matches:
        alternative_text = corrected_text[alternative_matches[-1].end():].strip()

        if alternative_text:
            corrected_text = alternative_text

    return corrected_text or text


QUALITATIVE_ABSENCE_PHRASES = (
    "맛이 없",
    "맛없",
    "재미없다",
    "재미 없",
    "재미가 없",
    "기운이 없",
    "입맛이 없",
    "입맛 없",
    "밥맛이 없",
    "밥맛 없",
    "식욕이 없",
    "식욕 없",
)


NEGATED_NEGATIVE_PHRASES = (
    "별로 걱정되지 않",
    "걱정 안",
    "안 아파",
    "안 아프",
    "안 힘든",
    "안 힘들",
    "안 나쁘",
    "안 불편",
    "안 외롭",
    "안 피곤",
    "안 무섭",
    "안 속상",
    "안 슬프",
    "안 우울",
    "힘들지 않",
    "나쁘지 않",
    "불편하지 않",
    "걱정되지 않",
    "걱정하지 않",
    "외롭지 않",
    "피곤하지 않",
    "무섭지 않",
    "속상하지 않",
    "슬프지 않",
    "우울하지 않",
)


NEGATED_NEGATIVE_PATTERN = re.compile(
    r"(?:"
    r"(?:아프|힘들|나쁘|외롭|무섭|슬프)(?:지|진|지는)\s*않|"
    r"(?:불편하|피곤하|속상하|우울하|걱정되)(?:지|진|지는)\s*않|"
    r"안\s*(?:아파|아프|힘들|나빠|나쁘|불편|외로|피곤|무서|속상|슬프|우울)|"
    r"걱정(?:은|이)?\s*안"
    r")"
)


NEGATED_POSITIVE_PHRASES = (
    "좋지는 않",
    "좋진 않",
    "좋지 않",
    "안 좋",
)


# "안/못 + 용언" 또는 "-지 않다"의 부정 표현을 형태소로 찾는다.
# 예전엔 "안" 뒤에 올 수 있는 동사 활용 조각을 문자 그대로 하드코딩한
# 정규식을 썼는데("보","봤" 등), "봐"(보다+아의 축약형)처럼 문자로는
# "보"를 포함하지 않는 축약형은 전혀 못 잡았다. 형태소 분석은 "봐"도
# 정확히 "보"(VV)로 인식하므로 활용형을 나열할 필요가 없어진다.
_NEGATION_ADVERBS = {"안", "못"}
# "생각이 안 나"/"기억이 안 나"의 "나"(생각나다/기억나다)는 행동이 아니라
# 회상 실패를 뜻하는 별도의 LOW_INFO 표현이라 여기서는 부정 행동으로
# 치지 않는다(_is_low_info_response가 이미 따로 처리한다).
_EXCLUDED_NEGATION_PREDICATES = {"나"}


def _negated_action_end_positions(text: str) -> list[int]:
    """부정 표현이 끝나는 글자 위치(exclusive)들을 문장 안에서 전부 찾는다."""
    tokens = _tokenize_cached(text)
    positions = []

    for index, token in enumerate(tokens):
        predicate_index = None

        if token.tag == "MAG" and token.form in _NEGATION_ADVERBS:
            next_index = index + 1
            if (
                next_index < len(tokens)
                and tokens[next_index].tag.startswith(_PREDICATE_TAG_PREFIXES)
                and tokens[next_index].form not in _EXCLUDED_NEGATION_PREDICATES
            ):
                predicate_index = next_index
        elif token.tag == "VX" and token.form == "않":
            # "-지 않다" 구성: "않다" 자체가 부정의 핵심 서술어다.
            predicate_index = index

        if predicate_index is None:
            continue

        end = tokens[predicate_index].start + tokens[predicate_index].len
        cursor = predicate_index + 1

        while cursor < len(tokens) and tokens[cursor].tag in _INFLECTION_TAGS:
            end = tokens[cursor].start + tokens[cursor].len
            cursor += 1

        positions.append(end)

    return positions


CONFIRMED_ACTION_CUES = (
    "먹었", "마셨어", "마셨", "갔", "다녀왔", "왔어", "봤", "보았",
    "들었", "샀", "만났", "통화", "전화", "연락", "쉬었", "잤",
    "누워", "산책했", "운동했", "청소했", "빨래했", "설거지했",
    "요리했", "목욕했", "정리했", "걸었",
)


def _get_confirmed_text_after_negated_action(text: str) -> str:
    text = _normalize_text(_get_final_correction_segment(text))
    positions = _negated_action_end_positions(text)

    if not positions:
        return ""

    suffix = text[positions[-1]:].strip(" ,.;!?")

    if _contains_any(suffix, CONFIRMED_ACTION_CUES):
        return suffix

    return ""


def _is_low_info_response(text: str) -> bool:
    text = _normalize_text(_get_final_correction_segment(text))

    if _contains_any(text, QUALITATIVE_ABSENCE_PHRASES):
        return False

    if text in {"아니"}:
        return True

    return _contains_any(
        text,
        (
            "몰라",
            "모르",
            "기억 안",
            "기억이 안",
            "생각 안",
            "생각이 안",
            "없어",
            "없다",
            "없었",
            "없네",
            "없다니까",
        ),
    )


OBSERVED_EMOTION_PATTERN = re.compile(
    r"(?:슬퍼|슬픈|힘들어|힘든|불안해|불안한|외로워|외로운|무서워|무서운|"
    r"피곤해|피곤한|아파|아픈|속상해|속상한|우울해|우울한|불편해|불편한|"
    r"화나|화난)\s*보(?:였|인다|여|였어|였다|였어요|였습니다|이네요)"
)


def _is_negative_response(text: str) -> bool:
    text = _get_final_correction_segment(text)
    negative_evidence_text = OBSERVED_EMOTION_PATTERN.sub("", text)

    for phrase in NEGATED_NEGATIVE_PHRASES:
        negative_evidence_text = negative_evidence_text.replace(phrase, "")

    negative_evidence_text = NEGATED_NEGATIVE_PATTERN.sub("", negative_evidence_text)

    return _contains_any(
        negative_evidence_text,
        (
            "별로",
            "싫",
            "힘들",
            "아프",
            "아파",
            "아팠",
            "우울",
            "속상하",
            "걱정",
            "불편",
            "무거웠",
            "화가",
            "화났",
            "짜증",
            "슬프",
            "슬펐",
            "외롭",
            "무섭",
            "불안",
            "서운",
            "피곤",
            "기운이 없",
            "기운 없",
            "재미없다",
            "재미 없",
            "맛없",
            "맛이 없",
            "입맛이 없",
            "입맛 없",
            "밥맛이 없",
            "밥맛 없",
            "식욕이 없",
            "식욕 없",
            "안 좋",
            "좋지 않",
            "나쁘",
            "나빴",
            "아쉽",
            "아쉬",
        ),
    )


def _is_positive_response(text: str) -> bool:
    text = _get_final_correction_segment(text)
    positive_evidence_text = text

    for phrase in NEGATED_POSITIVE_PHRASES:
        positive_evidence_text = positive_evidence_text.replace(phrase, "")

    for phrase in ("좋겠", "좋을 것 같", "좋을것 같"):
        positive_evidence_text = positive_evidence_text.replace(phrase, "")

    return _contains_any(
        positive_evidence_text,
        (
            "좋",
            "재밌",
            "즐겁",
            "맛있",
            "상쾌",
            "개운",
            "편안",
            "기쁘",
            "반갑",
        ),
    )


def _is_negated_action_response(text: str) -> bool:
    final_text = _get_final_correction_segment(text)
    return bool(_negated_action_end_positions(_normalize_text(final_text)))


def _is_short_response(text: str) -> bool:
    return len(_normalize_text(text)) <= 4


def _is_recall_recovery_response(text: str) -> bool:
    return _contains_any(
        _normalize_text(text),
        ("생각나다", "기억나다", "떠오르다"),
    )


def _count_recent_low_info_responses(texts: list[str], limit: int = 3) -> int:
    return sum(1 for text in texts[-limit:] if _is_low_info_response(text))


EXPLICIT_FOOD_KEYWORDS = (
    "식사",
    "음식",
    "밥",
    "반찬",
    "국",
    "찌개",
    "김치",
    "볶음밥",
    "피자",
    "간식",
    "물을",
    "물은",
    "물도",
    "물만",
    "물 마",
    "커피",
    "우유",
    "주스",
    "차를",
    "차도",
    "차 마",
)

SPECIFIC_FOOD_KEYWORDS = tuple(
    keyword
    for keyword in EXPLICIT_FOOD_KEYWORDS
    if keyword not in {"식사", "음식", "밥"}
)


def _is_unspecified_food_wish(text: str) -> bool:
    normalized = _normalize_text(text)
    return _contains_any(
        normalized,
        (
            "밥 먹고 싶",
            "밥을 먹고 싶",
            "음식 먹고 싶",
            "음식을 먹고 싶",
            "뭐 먹고 싶",
            "무언가 먹고 싶",
            "뭔가 먹고 싶",
        ),
    )


EXPLICIT_PLACE_KEYWORDS = (
    "집",
    "병원",
    "마트",
    "시장",
    "공원",
    "동네",
    "밖",
    "창밖",
)


FOOD_WISH_QUESTIONS = {
    "DEEPEN": [
        "어떤 음식이 가장 먼저 떠오르세요?",
        "그 음식이 생각난 이유가 있으세요?",
        "나중에 드신다면 누구와 같이 드시고 싶으세요?",
    ],
    "ANCHOR": [
        "같이 드시고 싶은 음식이 정해져 있으세요?",
        "그 음식은 어디에서 드시고 싶으세요?",
        "그 음식을 드실 때 가장 기대되는 점은 무엇인가요?",
    ],
}

FOOD_NEGATED_QUESTIONS = {
    "DEEPEN": [
        "그러셨군요. 그럼 오늘 드신 것이나 마신 것 중에 기억나는 게 있으세요?",
        "식사는 못 하셨군요. 대신 오늘 챙겨 드신 것이나 마신 게 있으세요?",
        "그럼 오늘 식사 대신 드신 간식이나 물이 있으세요?",
    ],
    "ANCHOR": [
        "오늘 먹는 일과 관련해서 기억나는 점이 있으세요?",
        "오늘 식사 이야기를 떠올리면 먼저 생각나는 게 있으세요?",
        "오늘 드시거나 마신 것 중 나중에 기억할 만한 게 있으세요?",
    ],
}


UNSPECIFIED_PLACE_QUESTIONS = {
    "DEEPEN": [
        "어디에 다녀오셨어요?",
    ],
    "ANCHOR": [
        "다녀오신 곳이 어디인지 말씀해주실래요?",
    ],
}

FOOD_APPETITE_QUESTIONS = {
    "DEEPEN": [
        "그러셨군요. 그래도 오늘 조금이라도 챙겨 드신 것이 있으세요?",
        "입맛이 없으셨군요. 그럴 때는 어떤 음식이 조금 편하세요?",
        "오늘은 물이나 간식처럼 가볍게 드신 것이 있으세요?",
    ],
    "ANCHOR": [
        "오늘 입맛이 없었던 걸 떠올리면 먼저 생각나는 시간이 있으세요?",
        "오늘 식사와 관련해서 기억나는 점이 있으세요?",
        "나중에 오늘 식사 이야기를 한다면 어떤 말이 먼저 떠오를까요?",
    ],
}


FOOD_DRINK_QUESTIONS = {
    "DEEPEN": [
        "그건 어디에서 마셨어요?",
        "그건 누구와 함께 마셨어요?",
        "마실 때 주변에 누가 있었어요?",
    ],
    "ANCHOR": [
        "아까 마신 것에서 가장 기억나는 점이 있으세요?",
        "그때 마신 것을 떠올리면 어떤 모습이 먼저 생각나세요?",
        "마시던 때를 떠올리면 주변에 무엇이 보였어요?",
    ],
}

FOOD_DRINK_WISH_QUESTIONS = [
    "그걸 마시고 싶은 이유가 있으세요?",
    "그건 누구와 같이 마시고 싶으세요?",
    "그건 언제쯤 마시고 싶으세요?",
]


PERSON_CONTACT_QUESTIONS = {
    "DEEPEN": [
        "그분과는 어떤 이야기를 나누셨어요?",
        "통화는 언제쯤 하셨어요?",
        "통화하실 때 어디에 계셨어요?",
    ],
    "ANCHOR": [
        "그 통화에서 가장 기억나는 말이 있으세요?",
        "통화하던 때를 떠올리면 어떤 장면이 먼저 생각나세요?",
        "그분과 나눈 이야기 중에 지금도 기억나는 게 있으세요?",
    ],
}

PERSON_NEGATED_CONTACT_QUESTIONS = {
    "DEEPEN": [
        "그분께 연락하게 되면 어떤 이야기를 나누고 싶으세요?",
        "그분께는 언제쯤 연락해 보고 싶으세요?",
    ],
    "ANCHOR": [
        "그분께 연락하면 가장 먼저 어떤 말을 하고 싶으세요?",
        "그분과 다시 이야기할 때 꼭 나누고 싶은 말이 있으세요?",
    ],
}

FUTURE_PERSON_CONTACT_QUESTIONS = [
    "전화하시면 어떤 이야기를 나누고 싶으세요?",
    "그분께 가장 먼저 어떤 말을 하고 싶으세요?",
]

PERSON_MEETING_PLACE_QUESTIONS = {
    "DEEPEN": [
        "그곳에서 그분과 무엇을 하셨어요?",
        "그곳에서 그분과 어떤 이야기를 나누셨어요?",
    ],
    "ANCHOR": [
        "그분과 그곳에는 얼마나 함께 계셨어요?",
        "그곳에서 그분과 함께한 일 중에 기억나는 게 있으세요?",
        "그곳에서 나눈 이야기 중에 기억나는 말이 있으세요?",
    ],
}

PERSON_IN_PERSON_CONVERSATION_QUESTIONS = {
    "DEEPEN": [
        "그분과 어떤 이야기를 나누셨어요?",
        "그분과 이야기하실 때 가장 즐거웠던 부분이 있으세요?",
    ],
    "ANCHOR": [
        "그분과 얼마나 오래 이야기하셨어요?",
        "그 이야기 중에 가장 기억나는 말이 있으세요?",
        "그분과 이야기하던 모습을 떠올리면 어떤 장면이 생각나세요?",
    ],
}

OBJECT_PLACEMENT_QUESTIONS = {
    "DEEPEN": [
        "그 자리에 두신 이유가 있으세요?",
        "옮겨 두고 나니 모습이 어떻게 달라졌어요?",
    ],
    "ANCHOR": [
        "옮겨 둔 자리를 떠올리면 주변에 무엇이 보이세요?",
        "그 물건을 옮긴 뒤 가장 먼저 눈에 띈 게 있으세요?",
    ],
}


def _has_medicine_reference(text: str) -> bool:
    normalized = re.sub(r"[^가-힣a-zA-Z0-9\s]", " ", str(text or ""))
    normalized = re.sub(r"\s+", " ", normalized).strip()

    return bool(
        re.search(
            r"(?:^|\s)약(?:을|은|이|도|만)?(?:\s|$)",
            normalized,
        )
        or re.search(r"(?:^|\s)약\s*(?:먹|드|챙)", normalized)
    )


def _has_standalone_soup_reference(text: str) -> bool:
    normalized = re.sub(r"[^가-힣a-zA-Z0-9\s]", " ", str(text or ""))
    normalized = re.sub(r"\s+", " ", normalized).strip()
    return bool(
        re.search(
            r"(?:^|\s)국(?:을|은|이|도|만)?(?:\s|$)",
            normalized,
        )
    )


def _has_confirmed_reference(texts: list[str], keyword: str) -> bool:
    for text in texts:
        normalized = _normalize_text(_get_final_correction_segment(text))
        confirmed_text = _get_confirmed_text_after_negated_action(normalized)
        evidence_text = confirmed_text or normalized

        if keyword in evidence_text and (
            confirmed_text or not _is_negated_action_response(normalized)
        ):
            return True

    return False


def _is_future_response(text: str) -> bool:
    text = _get_final_correction_segment(text)
    return _contains_any(
        text,
        (
            "내일",
            "모레",
            "예정",
            "갈 거",
            "갈거",
            "할 거",
            "할거",
            "먹을 거",
            "먹을거",
            "볼 거",
            "볼거",
            "만날 거",
            "만날거",
            "쉴 거",
            "쉴거",
            "잘 거",
            "잘거",
            "살 거",
            "살거",
            "갈게",
            "다녀올게",
            "할게",
            "먹을게",
            "볼게",
            "만날게",
            "쉴게",
            "살게",
            "올게",
            "가려고",
            "다녀오려고",
            "하려고",
            "먹으려고",
            "보려고",
            "만나려고",
            "쉬려고",
            "자려고",
            "사려고",
            "오려고",
            "갈래",
            "할래",
            "먹을래",
            "볼래",
            "만날래",
            "쉴래",
            "살래",
            "올래",
            "좋겠",
            "오실 거",
            "올 거",
            "올거",
        ),
        strict=True,
    )


def _is_wish_response(text: str) -> bool:
    text = _get_final_correction_segment(text)
    return _contains_any(
        text,
        (
            "가고 싶",
            "다녀오고 싶",
            "먹고 싶",
            "마시고 싶",
            "보고 싶",
            "만나고 싶",
            "하고 싶",
            "쉬고 싶",
            "자고 싶",
            "사고 싶",
            "듣고 싶",
        ),
        strict=True,
    )


def _has_everyday_object_action(text: str) -> bool:
    normalized = _normalize_text(text)
    action_pattern = r"(?:옮겼|뒀|두었|열었|닫았|닦았|사용했|썼)[가-힣]*"
    match = re.search(
        rf"(?:^|\s)([가-힣]{{2,}}?)(?:을|를|은|는|이|가)\s*{action_pattern}",
        normalized,
    ) or re.search(
        rf"(?:^|\s)([가-힣]{{2,}})\s+{action_pattern}",
        normalized,
    )

    if not match:
        return False

    object_word = match.group(1)
    return object_word not in {
        "오늘",
        "어제",
        "아까",
        "방금",
        "그거",
        "이거",
        "저거",
        "뭔가",
    }


def _detect_topic_in_text(text: str) -> str | None:
    if is_acknowledgement(text):
        return None
    text = _get_final_correction_segment(text)
    confirmed_text = _get_confirmed_text_after_negated_action(text)

    if confirmed_text:
        text = confirmed_text

    health_text = text.replace("약속", "").replace("예약", "")
    has_explicit_media = _contains_any(
        text,
        (
            "텔레비전",
            "티비",
            "방송",
            "프로그램",
            "노래",
            "가수",
            "미스터트롯",
            "드라마",
            "뉴스",
            "유튜브",
            "넷플릭스",
            "영상",
            "라디오",
        ),
    )
    has_media_consumption = (
        has_explicit_media
        and _contains_any(text, ("봤", "보았", "시청", "들었", "들어"))
    ) or "읽" in extract_predicate_forms(text)
    has_game_activity = _contains_any(
        text,
        (
            "게임",
            "오락",
            "퍼즐",
            "맞고",
            "고스톱",
            "화투",
            "바둑",
            "장기",
        ),
    )

    if _is_low_info_response(health_text) and _is_negative_response(health_text):
        return "NEGATIVE"

    if _is_low_info_response(health_text):
        return "LOW_INFO"

    if "기다려" in text and _contains_any(text, ("시간", "때", "저녁", "아침")):
        return "ROUTINE"

    has_explicit_health_context = _contains_any(
        health_text,
        (
            "병원",
            "진료",
            "의사",
            "간호",
            "아프",
            "아팠",
            "다쳤",
            "몸",
            "검사",
            "나아",
            "괜찮아졌",
            "호전",
        ),
    )
    has_body_part = _contains_any(
        health_text,
        ("허리", "팔", "다리", "무릎", "어깨", "배", "머리", "눈"),
    )
    has_health_symptom = _contains_any(
        health_text,
        ("불편", "쑤시", "삐끗", "저리", "안 좋", "통증"),
    )

    if (
        has_explicit_health_context
        or _has_medicine_reference(health_text)
        or (has_body_part and has_health_symptom)
    ):
        return "HEALTH"

    if (
        _contains_any(text, EXPLICIT_FOOD_KEYWORDS)
        and _contains_any(text, ("맛없", "맛이 없", "맛이 별로"))
    ):
        return "FOOD"

    if _contains_any(
        text,
        (
            "입맛이 없",
            "입맛 없",
            "밥맛이 없",
            "밥맛 없",
            "식욕이 없",
            "식욕 없",
        ),
    ):
        return "FOOD"

    if has_explicit_media and _contains_any(
        text,
        ("재미없다", "재미 없", "재미가 없", "별로"),
    ):
        return "MEDIA"

    if _contains_any(
        text,
        ("낮잠", "잠을", "잤", "쉬었", "쉬고", "쉬는", "휴식", "누워"),
    ):
        return "REST"

    if _is_negative_response(text):
        return "NEGATIVE"

    if _contains_any(
        text,
        (
            "날씨",
            "덥",
            "더웠",
            "춥",
            "비가",
            "비는",
            "눈이 와",
            "눈이 왔",
            "눈 왔",
            "눈 오는",
            "눈이 많이",
            "바람",
            "햇빛",
        ),
    ):
        return "WEATHER"

    if _contains_any(text, ("풍경", "벚꽃", "꽃이 피", "꽃이 폈")) or (
        "창밖" in text and _contains_any(text, ("봤", "보았", "보여", "보였"))
    ):
        return "SCENERY"

    if _contains_any(
        text,
        (
            "샀",
            "사왔",
            "사고 싶",
            "사려고",
            "살 거",
            "살거",
            "장 봤",
            "장봤",
            "장 보",
            "장보",
            "장 볼",
            "장볼",
            "장보러",
            "물건",
        ),
    ):
        return "SHOPPING"

    if _contains_any(
        text,
        (
            "먹",
            "마시",
            "마셨",
            "식사",
            "음식",
            "밥",
            "반찬",
            "찌개",
            "김치",
            "볶음밥",
            "피자",
            "간식",
            "식욕",
            "맛",
        ),
    ) or _has_standalone_soup_reference(text):
        return "FOOD"

    if _has_everyday_object_action(text):
        return "OBJECT"

    if has_game_activity:
        return "GAME"

    if _contains_any(
        text,
        (
            "낮잠",
            "잠을",
            "잤",
            "쉬었",
            "쉬고",
            "쉬는",
            "휴식",
            "누워",
        ),
    ):
        return "REST"

    if _contains_any(
        text,
        (
            "산책",
            "운동",
            "청소",
            "빨래",
            "설거지",
            "요리",
            "게임",
            "오락",
            "씻",
            "목욕",
            "정리",
            "쉬었",
        ),
    ):
        return "ACTIVITY"

    if _contains_any(
        text,
        (
            "아들",
            "딸",
            "동생",
            "형",
            "누나",
            "언니",
            "오빠",
            "엄마",
            "아빠",
            "어머니",
            "아버지",
            "남편",
            "아내",
            "조카",
            "사촌",
            "손주",
            "배우자",
            "가족",
            "친구",
            "그분",
            "연락",
            "통화",
            "만났",
            "이야기",
        ),
    ):
        return "PERSON"

    if (
        "집" in text
        and _contains_any(
            text,
            (
                "집에 있었",
                "집에만 있었",
                "집에서 있었",
                "집에서 지냈",
                "집에 머물",
                "집에서 머물",
                "집에서만",
                "집에만",
                "안 나갔",
                "못 나갔",
            ),
        )
        and not _contains_any(text, ("집 밖", "집밖"))
    ):
        return "HOME"

    if not has_media_consumption and _contains_any(
        text,
        (
            "집",
            "병원",
            "마트",
            "시장",
            "공원",
            "동네",
            "밖",
            "창밖",
            "어디",
            "다녀오",
            "갔",
            "갔다",
        ),
    ):
        return "PLACE"

    if has_media_consumption or _contains_any(
        text,
        (
            "텔레비전",
            "티비",
            "방송",
            "프로그램",
            "노래",
            "가수",
            "미스터트롯",
            "드라마",
            "뉴스",
            "유튜브",
            "넷플릭스",
            "영상",
            "라디오",
        ),
    ):
        return "MEDIA"

    return None


def _detect_recent_context_topic(cycle_texts: list[str]) -> str | None:
    for text in reversed(cycle_texts):
        topic = _detect_topic_in_text(text)

        if topic not in {None, "LOW_INFO", "NEGATIVE"}:
            return topic

    return None


def _detect_topic_from_question(question: str) -> str | None:
    question = _normalize_text(question)

    topic_cues = (
        ("HEALTH", ("병원", "약", "몸", "아프", "불편", "괜찮")),
        ("FOOD", ("음식", "드셨어", "먹었", "마셨어", "식사", "맛")),
        (
            "MEDIA",
            (
                "방송",
                "프로그램",
                "노래",
                "가수",
                "드라마",
                "뉴스",
                "티비",
                "유튜브",
                "넷플릭스",
                "영상",
                "라디오",
            ),
        ),
        ("SHOPPING", ("사신", "샀", "고르셨어", "장 보", "마트", "시장")),
        ("OBJECT", ("물건", "손에 자주", "자주 잡", "어디에 두")),
        ("HOME", ("집에서", "집 안", "집에 계실", "어느 방", "머문 자리")),
        ("REST", ("쉬", "낮잠", "휴식")),
        ("ROUTINE", ("기다려지는 시간", "기다리는 시간")),
        ("SCENERY", ("동네", "풍경", "창밖에서 본", "자주 보이는")),
        ("GAME", ("게임",)),
        ("ACTIVITY", ("하신 일", "하시던 일", "운동", "산책", "청소")),
        ("WEATHER", ("날씨", "더웠", "추웠", "바람", "햇빛")),
        ("PLACE", ("어디", "그곳", "장소", "다녀오신 곳")),
        (
            "PERSON",
            ("누구", "사람", "그분", "보고 싶은 분", "통화", "연락"),
        ),
    )

    for topic, cues in topic_cues:
        if _contains_any(question, cues):
            return topic

    return None


def _detect_conversation_topic(
    latest_text: str,
    cycle_texts: list[str],
    previous_question: str = "",
) -> str | None:
    # 최신 답변의 주제를 우선한다. 이전 답변까지 먼저 섞으면
    # 사용자가 새 주제로 넘어갔는데도 이전 주제 질문이 계속 나올 수 있다.
    latest_topic = _detect_topic_in_text(latest_text)
    context_topic = _detect_recent_context_topic(cycle_texts)
    previous_cycle_texts = list(cycle_texts)

    if previous_cycle_texts and previous_cycle_texts[-1].strip() == latest_text.strip():
        previous_cycle_texts = previous_cycle_texts[:-1]

    previous_context_topic = _detect_recent_context_topic(previous_cycle_texts)
    question_topic = _detect_topic_from_question(previous_question)
    question_answer_type = infer_answer_type(previous_question)

    if (previous_context_topic and _detect_memory_event_reference_topic(previous_question)
            in {None, _normalize_memory_event_topic(previous_context_topic)}
            and answers_event_detail(latest_text, previous_question, previous_cycle_texts)):
        return previous_context_topic

    question_actions = extract_memory_action_concepts(previous_question) or extract_predicate_forms(previous_question)
    answer_actions = extract_memory_action_concepts(latest_text) or extract_predicate_forms(latest_text)
    if (
        latest_topic not in {None, "LOW_INFO", "NEGATIVE"}
        and question_actions and answer_actions
        and question_actions.isdisjoint(answer_actions)
        and _is_episodic_recall_text(latest_text)
        and not is_acknowledgement(latest_text)
        and not _is_negated_action_response(latest_text)
        and not _is_future_response(latest_text)
        and not _is_wish_response(latest_text)
    ):
        return latest_topic

    if (
        question_topic == "HEALTH"
        and latest_topic in {None, "PLACE", "PERSON"}
        and not _contains_any(
            latest_text,
            ("텔레비전", "티비", "방송", "프로그램", "노래", "음식", "밥", "피자"),
        )
    ):
        return "HEALTH"

    if (
        previous_context_topic not in {None, "LOW_INFO", "NEGATIVE"}
        and (
            question_topic not in {None, "LOW_INFO", "NEGATIVE"}
            or question_answer_type
            in {
                MemoryAnswerType.PERSON,
                MemoryAnswerType.PLACE,
                MemoryAnswerType.TIME,
            }
        )
        and (
            question_topic == previous_context_topic
            or _is_referential_memory_followup(previous_question)
        )
        and latest_topic in {None, "PLACE", "PERSON"}
        and not _contains_any(
            latest_text,
            (
                "텔레비전",
                "티비",
                "방송",
                "프로그램",
                "노래",
                "가수",
                "드라마",
                "뉴스",
                "음식",
                "밥",
                "국",
                "찌개",
                "피자",
                "커피",
                "우유",
                "주스",
            ),
        )
    ):
        return previous_context_topic

    if latest_topic is None and question_topic is not None:
        return question_topic

    if latest_topic == "NEGATIVE" and question_topic == "HEALTH":
        return "HEALTH"

    if (
        latest_topic == "FOOD"
        and previous_context_topic == "HEALTH"
        and not _contains_any(latest_text, EXPLICIT_FOOD_KEYWORDS)
    ):
        return previous_context_topic

    if (
        latest_topic == "PLACE"
        and previous_context_topic == "HEALTH"
        and not _contains_any(latest_text, EXPLICIT_PLACE_KEYWORDS)
    ):
        return previous_context_topic

    if (
        latest_topic == "PLACE"
        and previous_context_topic == "WEATHER"
        and _contains_any(latest_text, ("안 나갔", "밖에 안", "집에 있었"))
    ):
        return previous_context_topic

    if (
        latest_topic == "MEDIA"
        and previous_context_topic == "PLACE"
        and not _contains_any(
            latest_text,
            ("텔레비전", "티비", "방송", "프로그램", "노래", "가수", "드라마", "뉴스"),
        )
    ):
        return previous_context_topic

    if (
        latest_topic == "WEATHER"
        and (previous_context_topic == "MEDIA" or question_topic == "MEDIA")
        and _contains_any(latest_text, ("소식", "예보", "뉴스", "방송"))
    ):
        return "MEDIA"

    if (
        latest_topic == "PERSON"
        and previous_context_topic == "PLACE"
        and _contains_any(latest_text, ("갈 거", "갈거", "가려고", "가고 싶"))
    ):
        return "PLACE"

    if (
        latest_topic == "NEGATIVE"
        and previous_context_topic is not None
        and _contains_any(latest_text, ("별로",))
    ):
        return previous_context_topic

    if latest_topic is not None:
        return latest_topic

    if context_topic is not None:
        return context_topic

    return None


def _count_consecutive_topic_turns(
    cycle_texts: list[str],
    current_topic: str | None,
    previous_question: str = "",
) -> int:
    if current_topic in {None, "LOW_INFO", "NEGATIVE"}:
        return 0

    count = 0
    last_index = len(cycle_texts) - 1
    question_topic = _detect_topic_from_question(previous_question)

    for index in range(last_index, -1, -1):
        text = cycle_texts[index]
        topic = _detect_topic_in_text(text)

        if topic in {"LOW_INFO", "NEGATIVE"}:
            break

        if topic is None and index == last_index:
            topic = question_topic or current_topic
        elif topic is None and count > 0 and _is_short_response(text):
            topic = current_topic

        if topic != current_topic:
            break

        count += 1

    return count


def _get_topic_aware_fallback_candidates(
    stage: str,
    latest_text: str,
    cycle_texts: list[str],
    previous_question: str = "",
    resolved_topic: str | None = None,
    known_answer_types: tuple[str, ...] = (),
    missing_answer_types: tuple[str, ...] = (),
) -> list[str]:
    topic = resolved_topic or _detect_conversation_topic(
        latest_text,
        cycle_texts,
        previous_question,
    )

    if topic is None:
        return []

    if (
        _is_wish_response(latest_text)
        or (
            not extract_memory_action_concepts(latest_text)
            and _is_wish_response(previous_question)
        )
    ) and topic in WISH_TOPIC_QUESTIONS:
        if topic == "FOOD" and _contains_any(
            latest_text,
            ("마시고 싶", "커피", "우유", "주스", "차를", "차 마"),
        ):
            return FOOD_DRINK_WISH_QUESTIONS

        candidates = WISH_TOPIC_QUESTIONS[topic]

        if topic == "FOOD":
            if _is_unspecified_food_wish(latest_text):
                candidates = [
                    question
                    for question in candidates
                    if "어떤 음식" in question
                ]
            else:
                candidates = [
                    question
                    for question in candidates
                    if "어떤 음식" not in question
                ]

        return candidates

    if (
        _is_future_response(latest_text)
        and topic == "PERSON"
        and _contains_any(latest_text, ("통화", "전화", "연락"))
    ):
        return FUTURE_PERSON_CONTACT_QUESTIONS

    if _is_future_response(latest_text) and topic in FUTURE_TOPIC_QUESTIONS:
        candidates = FUTURE_TOPIC_QUESTIONS[topic]

        if topic == "PLACE":
            if _contains_any(latest_text, ("내일", "모레", "오늘", "다음 주", "다음주")):
                candidates = [question for question in candidates if "언제쯤" not in question]

            if _detect_topic_in_text(latest_text) == "PERSON":
                candidates = [
                    question
                    for question in candidates
                    if "혼자" not in question and "함께 가실 분" not in question
                ]

        return candidates

    candidates = TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS.get(topic, {}).get(stage, [])

    missing_set = {
        str(answer_type or "").strip().upper()
        for answer_type in missing_answer_types
        if str(answer_type or "").strip()
    }

    if (
        missing_set
        and candidates
        and topic in MISSING_DETAIL_PRIORITIZED_TOPICS
    ):
        preferred_candidates = _get_episode_detail_question_candidates(
            topic,
            tuple(
                str(answer_type or "").strip().upper()
                for answer_type in missing_answer_types
                if str(answer_type or "").strip().upper() in missing_set
            ),
            latest_text,
            cycle_texts,
        )

        if not preferred_candidates:
            preferred_candidates = []

            for question in candidates:
                question_type = _detect_topic_from_question(question)
                inferred_answer_type = infer_answer_type(question).value

                if (
                    inferred_answer_type in missing_set
                    or _normalize_memory_event_topic(question_type) in missing_set
                ):
                    preferred_candidates.append(question)

        if preferred_candidates:
            candidates = preferred_candidates

    if topic == "LOW_INFO" and _count_recent_low_info_responses(cycle_texts) >= 2:
        candidates = REPEATED_LOW_INFO_QUESTIONS.get(stage, candidates)

    if topic == "ROUTINE" and extract_memory_clues(
        latest_text,
        MemoryAnswerType.ACTIVITY,
    ):
        candidates = [
            question
            for question in candidates
            if (
                "무엇을 하세요" not in question
                and "하는 일이 무엇" not in question
            )
        ]

    if topic == "FOOD":
        has_appetite_expression = _contains_any(
            latest_text,
            ("입맛", "밥맛", "식욕"),
        )
        has_drink_expression = _contains_any(
            latest_text,
            ("마셨어", "마셨", "마신", "마시"),
        )

        if has_appetite_expression:
            candidates = FOOD_APPETITE_QUESTIONS.get(stage, candidates)
        elif has_drink_expression:
            candidates = FOOD_DRINK_QUESTIONS.get(stage, candidates)
        elif _contains_any(latest_text, ("안 먹", "못 먹", "아직 안")):
            candidates = FOOD_NEGATED_QUESTIONS.get(stage, candidates)
        elif _contains_any(latest_text, ("먹고 싶", "먹고싶")):
            candidates = FOOD_WISH_QUESTIONS.get(stage, candidates)

        if "혼자" in latest_text:
            candidates = [
                question
                for question in candidates
                if "누구와 같이" not in question
            ]

        if not has_appetite_expression and _contains_any(
            latest_text,
            ("맛있", "맛없", "맛 없", "맛은", "맛이", "시원", "별로"),
        ):
            candidates = [
                question
                for question in candidates
                if "맛" not in question
            ]

        if "에서" in latest_text or "집" in latest_text:
            candidates = [
                question
                for question in candidates
                if "어디에서" not in question
            ]

    if topic == "PERSON" and _contains_any(
        latest_text,
        ("통화", "전화", "연락"),
    ):
        candidates = (
            PERSON_NEGATED_CONTACT_QUESTIONS.get(stage, candidates)
            if _is_negated_action_response(latest_text)
            else PERSON_CONTACT_QUESTIONS.get(stage, candidates)
        )

        if _contains_any(
            latest_text,
            ("아침", "점심", "저녁", "오전", "오후", "새벽", "밤에", "낮에"),
        ) or re.search(r"\d+\s*시", latest_text):
            candidates = [
                question
                for question in candidates
                if "언제" not in question
            ]

        if _contains_any(
            latest_text,
            ("집에서", "병원에서", "공원에서", "밖에서", "방에서", "거실에서"),
        ):
            candidates = [
                question
                for question in candidates
                if "어디" not in question
            ]

    if (
        topic == "PERSON"
        and "만났" in latest_text
        and _contains_any(latest_text, EXPLICIT_PLACE_KEYWORDS)
    ):
        candidates = PERSON_MEETING_PLACE_QUESTIONS.get(stage, candidates)

    if (
        topic == "PERSON"
        and _contains_any(latest_text, ("이야기", "대화"))
        and not _contains_any(latest_text, ("통화", "전화", "연락"))
    ):
        candidates = PERSON_IN_PERSON_CONVERSATION_QUESTIONS.get(stage, candidates)

    if topic == "OBJECT" and _contains_any(
        latest_text,
        ("옮겼", "뒀", "두었", "놓았", "놨어"),
    ):
        candidates = OBJECT_PLACEMENT_QUESTIONS.get(stage, candidates)

    if topic == "HEALTH":
        context = " ".join([latest_text, *cycle_texts])

        if _is_negative_response(latest_text) and _contains_any(
            latest_text,
            (
                "아프",
                "아팠",
                "다쳤",
                "불편",
                "쑤",
                "삐끗",
                "저리",
                "통증",
                "걷기",
                "움직",
            ),
        ):
            if _contains_any(latest_text, ("걷기", "움직")):
                candidates = [
                    "걷거나 움직이실 때도 계속 불편하세요?",
                ]
            else:
                candidates = [
                    question
                    for question in candidates
                    if "지금" in question
                ]

        if not _has_medicine_reference(context):
            candidates = [
                question
                for question in candidates
                if "약" not in question
            ]

        if not _has_confirmed_reference(
            [latest_text, *cycle_texts],
            "병원",
        ):
            candidates = [
                question
                for question in candidates
                if "병원" not in question
            ]

        if "혼자" in latest_text:
            candidates = [
                question
                for question in candidates
                if "혼자" not in question
            ]

        if _contains_any(latest_text, ("아침", "점심", "저녁", "오전", "오후")):
            candidates = [
                question
                for question in candidates
                if "언제" not in question
            ]

        if _contains_any(context, ("허리", "팔", "다리", "무릎", "어깨", "배", "머리")):
            candidates = [
                question
                for question in candidates
                if "어느 쪽" not in question
            ]

        if not _contains_any(
            context,
            ("아프", "아팠", "다쳤", "불편", "쑤시", "삐끗", "저리", "안 좋", "통증", "몸"),
        ):
            candidates = [
                question
                for question in candidates
                if (
                    "몸 상태" not in question
                    and "어느 쪽" not in question
                    and "괜찮으세요" not in question
                )
            ]

    if topic == "WEATHER":
        context = " ".join([latest_text, *cycle_texts])

        if not _contains_any(context, ("덥", "더웠", "햇빛")):
            candidates = [
                question
                for question in candidates
                if "더울" not in question and "더웠" not in question
            ]

    if topic == "MEDIA":
        context = " ".join([latest_text, *cycle_texts])
        corrected_latest_text = _get_final_correction_segment(latest_text)
        is_confirmed_song_not_broadcast = _contains_any(
            corrected_latest_text,
            ("노래", "가수", "들었"),
        ) and not _contains_any(
            corrected_latest_text,
            ("방송", "티비", "텔레비전", "프로그램", "뉴스", "드라마"),
        )
        has_specific_media_detail = _contains_any(
            context,
            (
                "뉴스",
                "드라마",
                "미스터트롯",
                "가수",
                "노래",
            ),
        )

        if not _contains_any(context, ("노래", "가수", "들었")):
            candidates = [
                question
                for question in candidates
                if "노래" not in question and "들을 때" not in question
            ]

        if not has_specific_media_detail:
            candidates = [
                question
                for question in candidates
                if "사람" not in question
            ]
        else:
            candidates = [
                question
                for question in candidates
                if "어떤 방송이나 프로그램" not in question
            ]

        if _contains_any(latest_text, ("재미없다", "재미 없", "별로")):
            candidates = [
                question
                for question in candidates
                if "사람" not in question and "기분" not in question
            ]

        if is_confirmed_song_not_broadcast:
            candidates = [
                question
                for question in candidates
                if "보실 때" not in question and "방송" not in question
            ]
        elif _contains_any(context, ("노래", "가수", "들었")):
            candidates = [
                question
                for question in candidates
                if "보실 때" not in question and "방송에서" not in question
            ]

        if "드라마" in context:
            candidates = [
                question
                for question in candidates
                if "노래" not in question
            ]

        if "뉴스" in context:
            candidates = [
                question
                for question in candidates
                if "사람" not in question and "노래" not in question
            ]

        if _contains_any(latest_text, ("좋았", "재밌", "즐겁")):
            candidates = [
                question
                for question in candidates
                if "기분" not in question
            ]

    if topic == "PERSON":
        context = " ".join([latest_text, *cycle_texts])

        if not _contains_any(
            context,
            (
                "통화",
                "전화",
                "대화",
                "이야기했",
                "연락",
                "말했",
                "만났",
                "다녀갔",
                "찾아왔",
                "들렀",
            ),
        ):
            candidates = [
                question
                for question in candidates
                if "이야기를 나누셨어" not in question
            ]

        if _contains_any(
            latest_text,
            ("이야기", "얘기", "말했"),
        ):
            candidates = [
                question
                for question in candidates
                if "어떤 이야기를" not in question
            ]

    if topic == "PLACE" and "혼자" in latest_text:
        candidates = [
            question
            for question in candidates
            if "혼자" not in question
        ]

    if topic == "PLACE" and not _contains_any(
        " ".join([latest_text, *cycle_texts]),
        (
            "병원",
            "마트",
            "시장",
            "공원",
            "동네",
            "약국",
            "은행",
            "식당",
            "카페",
            "경로당",
            "복지관",
            "교회",
            "성당",
            "절",
        ),
    ):
        candidates = UNSPECIFIED_PLACE_QUESTIONS.get(stage, candidates)

    if topic == "REST":
        if "에서" in latest_text:
            candidates = [
                question
                for question in candidates
                if "어디에서" not in question
            ]

        if _contains_any(latest_text, ("괜찮", "좋아졌", "나아졌")):
            candidates = [
                question
                for question in candidates
                if "몸이 조금 괜찮" not in question
            ]

    return candidates


def _get_stage_fallback_candidates(
    stage: str,
    latest_text: str,
    cycle_texts: list[str],
    has_anchorable_event: bool = True,
) -> list[str]:
    is_low_info = _is_low_info_response(latest_text)

    if is_low_info and _count_recent_low_info_responses(
        _build_generation_history(cycle_texts, latest_text),
    ) >= 2:
        return REPEATED_LOW_INFO_QUESTIONS.get(stage, SAFE_STAGE_FALLBACK_QUESTIONS[stage])

    if not has_anchorable_event and not is_low_info:
        return NO_EVENT_FALLBACK_QUESTIONS

    return SAFE_STAGE_FALLBACK_QUESTIONS[stage]


def _get_topic_openers() -> list[str]:
    return SAFE_OPENING_QUESTIONS


def _get_fixed_to_free_talk_opener(session_records: list[dict] | None) -> str:
    used_questions = _get_recent_ai_replies(session_records)
    previous_questions = _get_recent_ai_reply_list(session_records)
    start_index = len(session_records or []) % len(FIXED_TO_FREE_TALK_OPENERS)

    opener = _pick_non_repeated_question(
        candidates=FIXED_TO_FREE_TALK_OPENERS,
        used_questions=used_questions,
        previous_questions=previous_questions,
        start_index=start_index,
    )

    if opener:
        return opener

    return _pick_least_recent_question(
        candidates=FIXED_TO_FREE_TALK_OPENERS,
        previous_questions=previous_questions,
        start_index=start_index,
    )


def _get_topic_change_openers(current_topic: str | None) -> list[str]:
    topic_markers = {
        "PERSON": ("보고 싶은 사람", "보고 싶은 분"),
        "FOOD": ("드신 것", "음식"),
        "PLACE": ("다녀오신 곳", "동네", "집 안"),
        "HOME": ("집에서는", "집 안", "머무는 자리"),
        "MEDIA": ("방송", "노래"),
        "WEATHER": ("날씨",),
        "SHOPPING": ("물건", "시장", "마트"),
        "OBJECT": ("손에", "물건"),
        "ROUTINE": ("기다려지는 시간", "하루 중"),
        "SCENERY": ("풍경", "동네", "창밖"),
        "ACTIVITY": ("하시던 일",),
        "GAME": ("게임",),
    }
    markers = topic_markers.get(current_topic, ())

    if not markers:
        return _get_topic_openers()

    return [
        question
        for question in _get_topic_openers()
        if not _contains_any(question, markers)
    ]


_DENIED_CONVERSATION_INTENTS = {
    "OUTING": {
        "questionCues": ("나가", "바깥", "집 밖", "다녀오신 곳"),
        "answerCues": (
            "안 나갔",
            "나가지 않았",
            "외출 안",
            "외출하지 않았",
            "밖에 안",
            "바깥에도 안",
            "집에만 있었",
            "집에 있었",
            "다녀온 곳이 없",
            "간 곳이 없",
            "나간 곳이 없",
        ),
    },
}


def _get_denied_conversation_intents(
    session_records: list[dict] | None,
) -> set[str]:
    records = sorted(
        [record for record in session_records or [] if record.get("recordId")],
        key=lambda record: int(
            record.get("turnOrder") or record.get("recordId") or 0
        ),
    )
    denied = set()

    for index in range(1, len(records)):
        question = _normalize_text(records[index - 1].get("aiReplyText") or "")
        answer = _normalize_text(records[index].get("transcriptText") or "")

        for intent, patterns in _DENIED_CONVERSATION_INTENTS.items():
            if (
                _contains_any(question, patterns["questionCues"])
                and _contains_any(answer, patterns["answerCues"])
            ):
                denied.add(intent)

    return denied


def _latest_answer_denies_conversation_intent(
    session_records: list[dict] | None,
) -> bool:
    records = sorted(
        list(session_records or []),
        key=lambda record: int(
            record.get("turnOrder") or record.get("recordId") or 0
        ),
    )
    if len(records) < 2:
        return False

    question = _normalize_text(records[-2].get("aiReplyText") or "")
    answer = _normalize_text(records[-1].get("transcriptText") or "")

    return any(
        _contains_any(question, patterns["questionCues"])
        and _contains_any(answer, patterns["answerCues"])
        for patterns in _DENIED_CONVERSATION_INTENTS.values()
    )


def _get_recent_conversation_topics(
    session_records: list[dict] | None,
) -> tuple[str, ...]:
    recent_topics = []

    for record in (session_records or [])[-8:]:
        detected_topics = (
            _detect_topic_in_text(record.get("transcriptText") or ""),
            _detect_topic_from_question(record.get("aiReplyText") or ""),
        )

        for topic in detected_topics:
            normalized = _normalize_memory_event_topic(topic)
            if normalized != "UNGROUPED":
                recent_topics.append(normalized)

    return tuple(recent_topics)


def _get_contextual_topic_openers(
    current_topic: str | None,
    session_records: list[dict] | None,
    latest_text: str,
    candidates: list[str] | None = None,
) -> list[str]:
    candidates = list(candidates or _get_topic_change_openers(current_topic))
    denied_intents = _get_denied_conversation_intents(session_records)

    for intent in denied_intents:
        question_cues = _DENIED_CONVERSATION_INTENTS[intent]["questionCues"]
        candidates = [
            question
            for question in candidates
            if not _contains_any(question, question_cues)
        ]

    recent_topics = []
    context_parts = []

    for record in (session_records or [])[-12:]:
        transcript = _normalize_text(record.get("transcriptText") or "")
        reply = _normalize_text(record.get("aiReplyText") or "")

        if transcript:
            context_parts.append(transcript)
            topic = _detect_topic_in_text(transcript)
            if topic not in {None, "LOW_INFO", "NEGATIVE"}:
                recent_topics.append(topic)

        if reply:
            context_parts.append(reply)
            topic = _detect_topic_from_question(reply)
            if topic is not None:
                recent_topics.append(topic)

    normalized_latest = _normalize_text(latest_text)
    if normalized_latest:
        context_parts.append(normalized_latest)

    context_key = "|".join(context_parts)

    def opener_priority(question: str) -> tuple[int, int, str]:
        topic = _detect_topic_from_question(question)
        use_count = recent_topics.count(topic) if topic is not None else 0
        last_used = (
            max(
                index
                for index, recent_topic in enumerate(recent_topics)
                if recent_topic == topic
            )
            if topic in recent_topics
            else -1
        )
        tie_breaker = hashlib.sha256(
            f"{context_key}|{question}".encode("utf-8")
        ).hexdigest()
        return use_count, last_used, tie_breaker

    return sorted(candidates, key=opener_priority)


def _pick_non_repeated_question(
    candidates: list[str],
    used_questions: set[str],
    previous_questions: list[str] | None,
    start_index: int,
) -> str | None:
    if not candidates:
        return None

    previous_questions = previous_questions or []

    for offset in range(len(candidates)):
        question = candidates[(start_index + offset) % len(candidates)]

        if _normalize_text(question) in used_questions:
            continue

        if is_similar_to_previous_question(question, previous_questions):
            continue

        return question

    return None


def _pick_least_recent_question(
    candidates: list[str],
    previous_questions: list[str] | None,
    start_index: int,
) -> str:
    previous_questions = previous_questions or []
    rotated_candidates = [
        candidates[(start_index + offset) % len(candidates)]
        for offset in range(len(candidates))
    ]

    def last_similar_index(candidate: str) -> int:
        for index in range(len(previous_questions) - 1, -1, -1):
            if is_similar_to_previous_question(
                candidate,
                [previous_questions[index]],
            ):
                return index

        return -1

    return min(rotated_candidates, key=last_similar_index)


def _with_topic_change_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
    if _contains_any(
        question,
        ("괜찮습니다.", "그러셨군요.", "그렇군요.", "알겠습니다.", "그랬군요.", "좋으셨겠어요.", "아이고", "다행이네요."),
    ):
        return question

    latest_record = _get_latest_record(session_records)
    latest_text = str((latest_record or {}).get("transcriptText") or "")

    if _is_positive_response(latest_text):
        return f"좋으셨겠어요. {question}"

    if _is_negative_response(latest_text):
        return f"그러셨군요. {question}"

    index = len(session_records or []) % len(TOPIC_CHANGE_ACKNOWLEDGEMENTS)
    acknowledgement = TOPIC_CHANGE_ACKNOWLEDGEMENTS[index]
    return f"{acknowledgement} {question}"


def _get_latest_record(session_records: list[dict] | None) -> dict | None:
    records = [record for record in session_records or [] if record.get("recordId")]

    if not records:
        return None

    return max(
        records,
        key=lambda record: int(record.get("turnOrder") or record.get("recordId") or 0),
    )


def _build_episode_metadata(
    session_id: int,
    record_id: int,
    session_records: list[dict] | None,
    latest_text: str,
    stage: str,
    resolved_topic: str | None = None,
) -> dict:
    records = sorted(
        [record for record in session_records or [] if record.get("recordId")],
        key=lambda record: int(record.get("turnOrder") or record.get("recordId") or 0),
    )
    cycle_texts = _get_cycle_transcripts(records)
    topic = resolved_topic or _detect_conversation_topic(
        latest_text,
        cycle_texts,
        _get_question_answered_by_latest_record(records),
    )
    normalized_topic = topic if topic not in {None, "LOW_INFO"} else "UNGROUPED"
    normalized_stage = str(stage or "OPEN").upper()
    episode_id = episode_identity(records, session_id, record_id, normalized_topic)

    return {
        "episodeId": episode_id,
        "conversationTopic": normalized_topic,
        "conversationStage": normalized_stage,
    }


def _get_question_answered_by_latest_record(
    session_records: list[dict] | None,
) -> str:
    records = sorted(
        [record for record in session_records or [] if record.get("recordId")],
        key=lambda record: int(record.get("turnOrder") or record.get("recordId") or 0),
    )

    if len(records) < 2:
        return ""

    return str(records[-2].get("aiReplyText") or "")


def _is_after_recall_answer(session_records: list[dict] | None) -> bool:
    latest_record = _get_latest_record(session_records)

    if not latest_record:
        return False

    return str(latest_record.get("answerRole") or "").upper() == "RECALL"


def _with_recall_transition_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
    if _contains_any(
        question,
        ("괜찮습니다.", "그러셨군요.", "그렇군요.", "알겠습니다.", "그랬군요.", "좋으셨겠어요.", "아이고", "다행이네요."),
    ):
        return question

    latest_record = _get_latest_record(session_records)
    latest_text = str((latest_record or {}).get("transcriptText") or "")

    if _is_recall_recovery_response(latest_text):
        return f"아하, 생각나셨군요. {question}"

    if _is_positive_response(latest_text):
        return f"좋으셨겠어요. {question}"

    index = len(session_records or []) % len(RECALL_TRANSITION_ACKNOWLEDGEMENTS)
    acknowledgement = RECALL_TRANSITION_ACKNOWLEDGEMENTS[index]
    return f"{acknowledgement} {question}"


def _with_recall_question_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
    if _contains_any(
        question,
        ("좋으셨겠어요.", "그러셨군요.", "그렇군요.", "알겠습니다.", "그랬군요.", "아이고", "다행이네요."),
    ):
        return question

    return _with_topic_change_acknowledgement(
        question,
        session_records,
    )


def _get_after_recall_opening_candidates(recall_answer_text: str) -> list[str]:
    if _is_recall_recovery_response(recall_answer_text):
        return AFTER_RECALL_OPENING_QUESTIONS

    if _is_negative_response(recall_answer_text):
        return AFTER_NEGATIVE_RECALL_OPENING_QUESTIONS

    if _is_low_info_response(recall_answer_text):
        return AFTER_LOW_INFO_RECALL_OPENING_QUESTIONS

    if _is_short_response(recall_answer_text):
        return AFTER_SHORT_RECALL_OPENING_QUESTIONS

    return AFTER_RECALL_OPENING_QUESTIONS


def _analyze_conversation_turn(
    session_records: list[dict] | None,
    latest_text: str,
) -> ConversationTurnState:
    episode_records = active_episode_records(session_records)
    cycle_texts = [
        str(row.get("transcriptText") or "").strip()
        for row in episode_records
        if str(row.get("transcriptText") or "").strip()
    ]
    confirmed_text = _get_confirmed_text_after_negated_action(latest_text)
    analyzed_text = confirmed_text or latest_text
    conversation_history = list(cycle_texts)
    previous_question = _get_question_answered_by_latest_record(session_records)

    if analyzed_text != latest_text:
        for index in range(len(conversation_history) - 1, -1, -1):
            if conversation_history[index].strip() == latest_text.strip():
                conversation_history[index] = analyzed_text
                break

    topic = _detect_conversation_topic(
        analyzed_text,
        conversation_history,
        previous_question,
    )
    memory_evaluation = score_recall_memory_candidate(analyzed_text)
    frame_records = [dict(row) for row in episode_records]
    if frame_records and frame_records[-1].get("transcriptText") == latest_text:
        frame_records[-1]["transcriptText"] = analyzed_text
    else:
        frame_records.append({"recordId": -1, "transcriptText": analyzed_text,
                              "turnOrder": max((int(row.get("turnOrder") or row.get("recordId") or 0) for row in frame_records), default=0) + 1})
    event_frame = resolve_event_frames(frame_records).get(int(frame_records[-1].get("recordId") or 0), EventFrame())
    if event_frame.mode in NON_EXPERIENCED and event_frame.source_text != analyzed_text:
        topic = _detect_conversation_topic(event_frame.source_text, [event_frame.source_text], "") or topic
    has_real_content = "too_short_or_meaningless" not in memory_evaluation["reasons"]
    has_anchorable_event = not (
        set(memory_evaluation["reasons"])
        & {
            "too_short_or_meaningless",
            "forbidden_fixed_question_content",
            "incomplete_or_negated_action",
            "future_or_wish_expression",
            "uncertain_memory",
            "state_without_action",
        }
    )
    has_anchorable_event = has_anchorable_event and event_frame.allows_past_evidence
    intent_state = build_followup_intent_state(
        session_records, _build_generation_history(conversation_history, analyzed_text),
        _fallback_answer_type_for_topic(_normalize_memory_event_topic(topic)),
        GENERIC_EPISODE_DETAIL_VALUES,
    )
    should_change_topic = (
        _is_negated_action_response(latest_text)
        and not confirmed_text
        and _detect_topic_in_text(latest_text) != "FOOD"
        and not (
            topic == "PERSON"
            and _contains_any(latest_text, ("통화", "전화", "연락"))
        )
    )

    return ConversationTurnState(
        event_frame=event_frame,
        followup_intent=intent_state,
        latest_text=analyzed_text,
        conversation_history=tuple(conversation_history),
        previous_question=previous_question,
        topic=topic,
        consecutive_topic_turns=_count_consecutive_topic_turns(
            conversation_history,
            topic,
            previous_question,
        ) if not any(row.get("episodeId") for row in episode_records) else len(episode_records),
        after_recall_answer=_is_after_recall_answer(session_records),
        should_change_topic=should_change_topic,
        is_memory_candidate=bool(memory_evaluation["isValid"]) and event_frame.allows_past_evidence,
        needs_memory_detail=(
            has_real_content
            and has_anchorable_event
            and topic is not None
            and topic != "PERSON"
            and not memory_evaluation["isValid"]
            and not _is_low_info_response(analyzed_text)
            and not _is_negative_response(analyzed_text)
        ),
        has_real_content=has_real_content,
        has_anchorable_event=has_anchorable_event,
        has_sufficient_detail=(
            event_frame.allows_past_evidence
            and (has_anchorable_event or event_frame.mode == EventMode.EXPERIENCED)
            and intent_state.has_sufficient_detail
        ),
        is_unanswered=_is_low_info_response(analyzed_text),
    )


def _decide_next_conversation_action(
    state: ConversationTurnState,
    candidate_count: int,
    *,
    available_answer_types: tuple[str, ...] | None = None,
    repeated_unanswered: bool = False,
) -> ConversationDecision:
    return decide_conversation_action(
        candidate_count=candidate_count,
        after_recall_answer=state.after_recall_answer,
        should_change_topic=state.should_change_topic,
        needs_memory_detail=state.needs_memory_detail,
        has_followup_context=state.topic is not None,
        consecutive_topic_turns=state.consecutive_topic_turns,
        has_sufficient_detail=state.has_sufficient_detail,
        is_unanswered=state.is_unanswered,
        available_answer_types=available_answer_types,
        repeated_unanswered=repeated_unanswered,
    )


@timed_stage("reply_plan")
def _plan_next_normal_reply(
    candidate_count: int,
    session_records: list[dict] | None = None,
    latest_text: str = "",
) -> ConversationReply:
    engine = os.getenv("MIDAS_DIALOGUE_ENGINE", "legacy").lower()
    if is_greeting(latest_text) or engine not in {"llm", "llm_reply"}:
        return _plan_legacy_normal_reply(candidate_count, session_records, latest_text)

    # Fixed questions and recall linkage run in process_voice_reply first.
    # Evidence proposals are deliberately not passed to scoring or persistence.
    records = ordered_records(session_records)
    if engine == "llm_reply":
        result = propose_reply_only(records, require_continuation=True,
                                    allow_recall_transition=bool(records and records[-1].get("answerRole") == "RECALL"))
    else:
        generate = propose_reply_only if engine == "llm_reply" else propose_dialogue
        result = generate(records)
    logger.info("dialogue_engine=%s status=%s reason=%s elapsed_ms=%s",
                engine, result.status, result.reason, result.elapsed_ms)
    if result.status == "PROPOSED" and result.proposal is not None:
        proposal = result.proposal
        stage = {"FOLLOW_UP": "DEEPEN", "CHANGE_TOPIC": "OPEN",
                 "ACKNOWLEDGE": "DEEPEN", "CLARIFY": "CLARIFY"}[proposal.action]
        return ConversationReply(proposal.reply.strip(), stage, None, "llm_" + proposal.action.lower())
    fallback = _plan_legacy_normal_reply(candidate_count, session_records, latest_text,
                                         allow_llm_rewrite=False)
    if engine == "llm_reply" and not conversation_control(latest_text):
        candidates = fallback_candidates(records, fallback.text, fallback.stage, (
            (FIXED_TO_FREE_TALK_OPENERS[0], "FOOD"),
            (FIXED_TO_FREE_TALK_OPENERS[2], "MEDIA"),
            (FIXED_TO_FREE_TALK_OPENERS[3], "PERSON"),
            *((text, infer_followup_target(text)) for text in _get_topic_openers()),
        ))
        for text, action, target in candidates:
            if action == "CHANGE_TOPIC":
                text = _with_topic_change_acknowledgement(text, records)
            checked = validate_local_voice_reply(records, text, action, target)
            if checked.status == "PROPOSED":
                return ConversationReply(text, "DEEPEN" if action == "FOLLOW_UP" else "OPEN", None,
                                         "voice_continuation_fallback")
        return ConversationReply("지금 나누고 싶은 이야기가 있으세요?", "OPEN", None,
                                 "no_grounded_question")
    return fallback


def _plan_legacy_normal_reply(
    candidate_count: int,
    session_records: list[dict] | None = None,
    latest_text: str = "",
    *, allow_llm_rewrite: bool = True,
) -> ConversationReply:
    if is_greeting(latest_text):
        return ConversationReply(
            text="안녕하세요. " + _get_fixed_to_free_talk_opener(session_records),
            stage="OPEN", topic=None, reason="greeting",
        )
    state = _analyze_conversation_turn(session_records, latest_text)
    if not state.after_recall_answer and short_reference(latest_text):
        reference = resolve_short_reference(
            session_records or [], latest_text,
            _fallback_answer_type_for_topic(_normalize_memory_event_topic(state.topic)),
        )
        if reference.clarified_before:
            return ConversationReply("괜찮아요. 이 이야기는 여기까지 해도 돼요.", "OPEN", state.topic, "clarification_exhausted")
        if reference.source_text:
            topic = _detect_conversation_topic(reference.source_text, [reference.source_text], "")
            return ConversationReply("아, 그 이야기였군요.", "CLARIFY", topic, "grounded_reference")
        return ConversationReply("어떤 것을 말씀하시는지 조금만 더 알려주시겠어요?", "CLARIFY", state.topic, "clarify_reference_once")
    if not state.after_recall_answer and state.event_frame.mode == EventMode.STATE and is_current_state(state.event_frame.source_text):
        return ConversationReply("그렇군요. 지금은 편하게 이야기 나눠요.", "OPEN", state.topic, "respond_to_current_state")
    previous_questions = _get_recent_ai_reply_list(session_records)
    used_questions = _get_recent_ai_replies(session_records)
    history = list(state.conversation_history)
    intent_state = state.followup_intent or build_followup_intent_state(
        session_records, _build_generation_history(history, state.latest_text),
        _fallback_answer_type_for_topic(_normalize_memory_event_topic(state.topic)),
        GENERIC_EPISODE_DETAIL_VALUES,
    )
    completed_event = state.has_anchorable_event or state.event_frame.mode == EventMode.EXPERIENCED
    expected_types = EXPECTED_EPISODE_DETAIL_TYPES_BY_TOPIC.get(
        _normalize_memory_event_topic(state.topic), (),
    )
    if state.event_frame.mode == EventMode.WISH:
        primary_type = _fallback_answer_type_for_topic(_normalize_memory_event_topic(state.topic)).value
        expected_types = tuple(dict.fromkeys(slot for slot in (primary_type, "REASON", "PERSON") if slot != "UNKNOWN"))
    if state.event_frame.mode in {EventMode.WISH, EventMode.PLAN} and extract_memory_action_concepts(state.event_frame.source_text):
        # A declared intended action is already known even when it is ineligible
        # for past-event memory extraction.
        expected_types = tuple(slot for slot in expected_types if slot != "ACTIVITY")
    missing_types = tuple(
        slot for slot in expected_types
        if slot not in intent_state.known_types and slot not in intent_state.attempted_types
    )
    recent = ordered_records(session_records)[-2:]
    repeated_unanswered = (
        state.is_unanswered and len(recent) == 2
        and all(not row.get("answerRole") and _is_low_info_response(str(row.get("transcriptText") or ""))
                for row in recent)
    )
    decision = _decide_next_conversation_action(
        state, candidate_count,
        available_answer_types=missing_types if completed_event or state.event_frame.mode in {EventMode.WISH, EventMode.PLAN} else None,
        repeated_unanswered=repeated_unanswered,
    )
    stage = decision.stage
    reason = decision.reason
    question = None

    if decision.action == ConversationAction.ACKNOWLEDGE:
        return ConversationReply(
            text="알겠어요. 잠시 쉬어 가도 괜찮아요.",
            stage=decision.stage, topic=state.topic, reason=decision.reason,
        )

    if not state.after_recall_answer and declines_detail(latest_text):
        return ConversationReply("괜찮아요. 바로 떠오르지 않으면 넘어가도 돼요.", "OPEN", state.topic, "respect_uncertain_answer")

    if is_repeat_reminder(state.latest_text):
        candidates = _get_contextual_topic_openers(state.topic, session_records, state.latest_text)
        question = _pick_non_repeated_question(candidates, used_questions, previous_questions, 0)
        return ConversationReply(
            text="같은 내용을 다시 여쭤봐서 미안해요. " + (question or "잠시 쉬어도 괜찮아요."),
            stage="OPEN", topic=state.topic, reason="repair_repeated_question",
        )

    if decision.action == ConversationAction.FOLLOW_UP:
        if completed_event:
            target_types = (decision.target_answer_type,) if decision.target_answer_type else ()
            content_questions = _get_episode_detail_question_candidates(
                state.topic, target_types, state.latest_text, history,
            )
            candidates = render_event_followups(target_types, content_questions)
        else:
            # Wishes, plans and states must not be phrased as completed events.
            candidates = _get_topic_aware_fallback_candidates(
                stage, state.event_frame.source_text if state.event_frame.mode in NON_EXPERIENCED else state.latest_text,
                history, state.previous_question,
                resolved_topic=state.topic, missing_answer_types=missing_types,
            )
            if decision.target_answer_type:
                candidates = [candidate for candidate in candidates
                              if infer_followup_target(candidate) == decision.target_answer_type]
                if not candidates and decision.target_answer_type == "REASON":
                    candidates = ["어떤 점 때문에 그렇게 하고 싶으세요?"]
        if state.topic is None:
            candidates = _get_stage_fallback_candidates(
                stage, state.latest_text, history, state.has_anchorable_event,
            )
        question = _pick_non_repeated_question(
            [candidate for candidate in candidates if intent_state.allows(candidate)],
            used_questions, previous_questions, start_index=0,
        )
        if question is None:
            reason = "grounded_followups_exhausted"

    if question is None:
        stage = "OPEN"
        preferred = (
            _get_after_recall_opening_candidates(state.latest_text)
            if state.after_recall_answer else LOW_BURDEN_TOPIC_CHANGE_QUESTIONS
        )
        candidates = _get_contextual_topic_openers(
            state.topic, session_records, state.latest_text, preferred,
        )
        alternatives = _get_contextual_topic_openers(
            state.topic, session_records, state.latest_text,
        )
        candidates = list(dict.fromkeys([*candidates, *alternatives]))
        question = _pick_non_repeated_question(
            candidates, used_questions, previous_questions, start_index=0,
        )
        if question is None:
            return ConversationReply(
                text="잠시 쉬셔도 괜찮아요.", stage="OPEN", topic=state.topic,
                reason="no_suitable_question",
            )

    generated = generate_safe_followup_question(
        conversation_history=_build_generation_history(history, state.latest_text),
        stage=stage, fallback_question=question, previous_questions=previous_questions,
    ) if allow_llm_rewrite else {"nextQuestion": question, "shouldChangeTopic": False}
    # The renderer may phrase a decision, but cannot change the conversation state.
    reply = (
        question if generated.get("shouldChangeTopic")
        else str(generated.get("nextQuestion") or question)
    )
    if stage != "OPEN" and intent_state and not intent_state.preserves_target(question, reply):
        reply = question
    if stage == "OPEN":
        reply = (
            _with_recall_transition_acknowledgement(reply, session_records)
            if state.after_recall_answer
            else _with_topic_change_acknowledgement(reply, session_records)
        )
    return ConversationReply(text=reply, stage=stage, topic=state.topic, reason=reason)


def _get_next_normal_question(
    candidate_count: int,
    session_records: list[dict] | None = None,
    latest_text: str = "",
) -> str:
    return _plan_next_normal_reply(candidate_count, session_records, latest_text).text


def _save_next_conversation_reply(
    record_id: int,
    session_id: int,
    candidate_count: int,
    session_records: list[dict] | None,
    latest_text: str,
) -> str:
    plan = _plan_next_normal_reply(
        candidate_count,
        session_records,
        latest_text=latest_text,
    )
    metadata = _build_episode_metadata(
        session_id=session_id,
        record_id=record_id,
        session_records=session_records,
        latest_text=latest_text,
        stage=plan.stage,
        resolved_topic=plan.topic,
    )
    _save_ai_reply(
        record_id=record_id,
        reply_text=plan.text,
        episode_id=metadata["episodeId"],
        conversation_topic=metadata["conversationTopic"],
        conversation_stage=metadata["conversationStage"],
    )
    return plan.text


def _get_current_transcript(
    session_records: list[dict],
    current_record_id: int,
    fallback_text: str = "",
) -> str:
    for record in session_records:
        if int(record.get("recordId") or 0) == int(current_record_id):
            return record.get("transcriptText") or fallback_text

    return fallback_text


def _get_record_by_id(
    session_records: list[dict],
    record_id: int,
) -> dict | None:
    for record in session_records:
        if int(record.get("recordId") or 0) == int(record_id):
            return record

    return None


def _with_current_answer_role(
    session_records: list[dict],
    current_record_id: int,
    answer_role: str,
    recall_question_id: int | None = None,
) -> list[dict]:
    updated_records = []
    found_current_record = False

    for record in session_records:
        updated_record = dict(record)

        if updated_record.get("recordId") == current_record_id:
            updated_record["answerRole"] = answer_role
            found_current_record = True

            if recall_question_id is not None:
                updated_record["recallQuestionId"] = recall_question_id

        updated_records.append(updated_record)

    if not found_current_record:
        updated_records.append(
            {
                "recordId": current_record_id,
                "transcriptText": "",
                "answerRole": answer_role,
                "recallQuestionId": recall_question_id,
                "parentRecordId": None,
            }
        )

    return updated_records


def _find_pending_recall_question_id(
    session_records: list[dict],
    current_record_id: int,
    recall_questions: list[dict] | None = None,
) -> int | None:
    return select_pending_recall(session_records, current_record_id, recall_questions)


@serialize_session
@serialize_record
def process_voice_reply(
    record_id: int,
    session_id: int,
    user_id: int,
    transcript_text: str = "",
    speech_risk_score: float = 0.0,
    run_realtime_analysis: bool = False,
):
    started_at = perf_counter()
    logger.info("AI 답변 및 매핑 시작: recordId=%s sessionId=%s", record_id, session_id)

    try:
        session_records = records_through_current(_fetch_session_records(session_id), record_id)

        if not session_records:
            logger.error(
                "세션 레코드를 확인하지 못해 역할을 추측하지 않고 처리 중단: "
                "recordId=%s sessionId=%s",
                record_id,
                session_id,
            )
            return

        current_record = _get_record_by_id(session_records, record_id)

        if str((current_record or {}).get("aiReplyText") or "").strip():
            logger.info(
                "이미 AI 답변이 저장된 레코드이므로 중복 처리를 건너뜀: recordId=%s",
                record_id,
            )
            return

        current_answer_role = str(
            (current_record or {}).get("answerRole") or ""
        ).upper()

        current_text = _get_current_transcript(session_records, record_id, transcript_text)
        control = conversation_control(current_text)
        if control == "PAUSE":
            _save_ai_reply(record_id=record_id, reply_text="네, 잠시 쉬어요. 이야기해 주셔서 고마워요.",
                           conversation_stage="PAUSED")
            return

        if current_answer_role == "RECALL":
            if current_record.get("recallQuestionId") is None:
                raise DialogueStateUnavailable("linked_recall_question_missing")
            # The answer link committed before a failed reply write. Resume only
            # the continuation, without selecting another question or grading twice.
            _save_next_conversation_reply(
                record_id=record_id,
                session_id=session_id,
                candidate_count=0,
                session_records=session_records,
                latest_text=current_text,
            )
            return

        if current_answer_role == "INITIAL":
            current_question_id = (current_record or {}).get("recallQuestionId")
            if current_question_id is None:
                raise DialogueStateUnavailable("initial_recall_question_missing")
            matching = [q for q in _fetch_recall_questions(user_id)
                        if str(q.get("questionId")) == str(current_question_id)]
            if len(matching) != 1:
                raise DialogueStateUnavailable("initial_recall_question_unavailable")
            question = matching[0]
            question_text = str(question.get("questionText") or "").strip()

            if not recall_is_due(question, turn_position(session_records, record_id)):
                _save_next_conversation_reply(
                    record_id=record_id, session_id=session_id, candidate_count=0,
                    session_records=session_records, latest_text=current_text,
                )
                return

            if not question_text:
                logger.error(
                    "연결된 회상 질문 문구를 찾지 못해 복구 중단: recordId=%s questionId=%s",
                    record_id,
                    current_question_id,
                )
                return

            _save_ai_reply(record_id=record_id, reply_text=question_text,
                           presented_recall_question_id=int(current_question_id))
            logger.info(
                "INITIAL 연결 후 누락된 회상 질문 저장 복구: recordId=%s questionId=%s",
                record_id,
                current_question_id,
            )
            return

        if current_answer_role == "FIXED":
            fixed_status = _get_fixed_question_status(user_id)
            fixed_answer_count = _count_fixed_answers(session_records)

            if not fixed_status["onboardingDone"] and fixed_answer_count < len(FIXED_QUESTIONS):
                _save_ai_reply(
                    record_id=record_id,
                    reply_text=FIXED_QUESTIONS[fixed_answer_count]["questionText"],
                )
                logger.info(
                    "연결 완료된 고정 답변의 다음 질문 저장 재개: recordId=%s nextIndex=%s",
                    record_id,
                    fixed_answer_count,
                )
                return

            # 온보딩 중 5번째까지 다 왔거나, 온보딩 이후 하루 1문항을 이미 답한 경우 —
            # 자유대화로 넘어간다.
            if not _mark_fixed_questions_done_today(
                user_id,
                onboarding=not fixed_status["onboardingDone"],
            ):
                return
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_fixed_to_free_talk_opener(session_records),
            )
            logger.info("고정 답변 이후 자유대화 질문 저장 재개: recordId=%s", record_id)
            return

        # 1. 직전 회상 질문에 대한 답변이면 RECALL로 연결하고 자유대화로 복귀한다.
        recall_questions = _fetch_recall_questions(user_id)
        pending_recall_question_id = _find_pending_recall_question_id(
            session_records=session_records,
            current_record_id=record_id,
            recall_questions=recall_questions,
        )

        if control == "SKIP":
            pending_question = next((question for question in recall_questions
                                     if str(question.get("questionId")) == str(pending_recall_question_id)), {})
            skipped_presented_recall = pending_recall_question_id is not None and _was_recall_question_presented(
                session_records=session_records,
                current_record_id=record_id,
                recall_question_id=pending_recall_question_id,
                question_text=str(pending_question.get("questionText") or ""),
            )
            _save_ai_reply(
                record_id=record_id,
                reply_text="네, 다른 이야기로 넘어갈게요. " + _get_fixed_to_free_talk_opener(session_records),
                conversation_stage="RECALL_SKIPPED" if skipped_presented_recall else "TOPIC_SKIPPED",
            )
            return

        if pending_recall_question_id is not None:
            question_meta = next((question for question in recall_questions
                                  if str(question.get("questionId")) == str(pending_recall_question_id)),
                                 {"questionId": pending_recall_question_id})
            pending_question_text = str(question_meta.get("questionText") or "").strip()
            if not pending_question_text:
                pending_question_text = _find_recall_question_text(user_id, pending_recall_question_id)

            if not pending_question_text:
                logger.error(
                    "대기 중인 회상 질문 문구를 찾지 못해 답변 연결 중단: recordId=%s questionId=%s",
                    record_id,
                    pending_recall_question_id,
                )
                return

            if not _was_recall_question_presented(
                session_records=session_records,
                current_record_id=record_id,
                recall_question_id=pending_recall_question_id,
                question_text=pending_question_text,
            ):
                _save_ai_reply(
                    record_id=record_id,
                    reply_text=pending_question_text,
                    presented_recall_question_id=pending_recall_question_id,
                )
                logger.warning(
                    "표시되지 않은 회상 질문을 먼저 복구하고 현재 답변의 RECALL 연결은 보류: "
                    "recordId=%s questionId=%s",
                    record_id,
                    pending_recall_question_id,
                )
                return

            current_text = _get_current_transcript(session_records, record_id, transcript_text)
            response_intent = classify_recall_response(current_text)
            if response_intent == RecallResponseIntent.DISPUTE:
                _save_ai_reply(
                    record_id=record_id,
                    reply_text="제가 앞선 말씀을 잘못 연결했을 수 있겠네요. 이 질문은 넘어갈게요. "
                               + _get_fixed_to_free_talk_opener(session_records),
                    conversation_stage="RECALL_SKIPPED",
                )
                return
            if response_intent == RecallResponseIntent.CLARIFY:
                previous = ordered_records(session_records)
                current_index = next((i for i, row in enumerate(previous) if row.get("recordId") == record_id), 0)
                if current_index and previous[current_index - 1].get("conversationStage") == "RECALL_CLARIFY":
                    _save_ai_reply(
                        record_id=record_id,
                        reply_text="제가 질문을 분명하게 드리지 못했네요. 다른 이야기로 넘어갈게요. "
                                   + _get_fixed_to_free_talk_opener(session_records),
                        conversation_stage="RECALL_SKIPPED",
                    )
                    return
                _save_ai_reply(
                    record_id=record_id,
                    reply_text=clarify_recall_question(question_meta, pending_question_text, session_records),
                    conversation_stage="RECALL_CLARIFY",
                    presented_recall_question_id=pending_recall_question_id,
                )
                return

            linked = _link_recall_question(
                record_id=record_id,
                recall_question_id=pending_recall_question_id,
                answer_role="RECALL",
            )

            if not linked:
                logger.error(
                    "회상 답변 연결 실패로 다음 단계 중단: recordId=%s questionId=%s",
                    record_id,
                    pending_recall_question_id,
                )
                return

            logger.info(
                "회상 질문 답변으로 매핑 완료: recordId=%s questionId=%s",
                record_id,
                pending_recall_question_id,
            )

            if run_realtime_analysis:
                try:
                    analyze_session_recall(
                        user_id=user_id,
                        session_id=session_id,
                        speech_risk_score=speech_risk_score,
                        base_url=SPRING_BASE_URL,
                    )
                except Exception as e:
                    logger.warning("실시간 회상 분석 건너뜀: %s", e)

            records_after_recall = _with_current_answer_role(
                session_records=session_records,
                current_record_id=record_id,
                answer_role="RECALL",
                recall_question_id=pending_recall_question_id,
            )

            _save_next_conversation_reply(
                record_id=record_id,
                session_id=session_id,
                candidate_count=0,
                session_records=records_after_recall,
                latest_text=_get_current_transcript(
                    records_after_recall,
                    record_id,
                    transcript_text,
                ),
            )
            return

        # 2. 고정 질문 처리
        #    - 온보딩(최초) 중이면 5개를 순서대로 다 물어본다.
        #    - 온보딩이 끝났으면 그 이후로는 매일 5개 중 1개만(userId+날짜로 고정된 무작위) 물어본다.
        #    - 오늘 이미 물어봤으면 바로 자유대화로 진입한다.
        fixed_status = _get_fixed_question_status(user_id)
        already_done_today = fixed_status["doneToday"]

        if already_done_today:
            logger.info("오늘 고정 질문을 이미 완료함. 현재 답변부터 자유대화로 처리.")

        elif fixed_status["onboardingDone"]:
            daily_index = _pick_daily_fixed_question_index(user_id)

            if not _link_fixed_question_answer(
                record_id=record_id,
                user_id=user_id,
                question_data=FIXED_QUESTIONS[daily_index],
            ):
                return

            logger.info(
                "온보딩 이후 하루 1회 고정 질문 완료: index=%s. 자유대화 단계로 전환.",
                daily_index,
            )
            if not _mark_fixed_questions_done_today(user_id, onboarding=False):
                return

            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_fixed_to_free_talk_opener(session_records),
            )
            return

        else:
            fixed_answer_count = _count_fixed_answers(session_records)

            if fixed_answer_count < len(FIXED_QUESTIONS):
                if not _link_fixed_question_answer(
                    record_id=record_id,
                    user_id=user_id,
                    question_data=FIXED_QUESTIONS[fixed_answer_count],
                ):
                    return

                next_index = fixed_answer_count + 1

                if next_index < len(FIXED_QUESTIONS):
                    _save_ai_reply(
                        record_id=record_id,
                        reply_text=FIXED_QUESTIONS[next_index]["questionText"],
                    )

                    logger.info("초기 고정 질문 제공 완료: nextIndex=%s", next_index)
                    return

                logger.info("초기 고정 질문 5개(온보딩) 완료. 자유대화 단계로 전환.")
                if not _mark_fixed_questions_done_today(user_id, onboarding=True):
                    return

                _save_ai_reply(
                    record_id=record_id,
                    reply_text=_get_fixed_to_free_talk_opener(session_records),
                )
                return

        # Unresolved references need repair first. Low-information/current-state
        # answers are not new evidence, but must not starve an older memory.
        if is_repeat_reminder(current_text) or short_reference(current_text):
            _save_next_conversation_reply(record_id, session_id, 0, session_records, current_text)
            return

        # 3. 충분히 축적되고 한 턴 이상 지난 memoryPoint가 있으면 회상 질문 생성
        # No writes occur on this path before candidate selection. Reuse the
        # turn-bounded snapshot instead of paying for a second HTTP read.
        updated_records = session_records

        transcripts = _extract_recall_candidate_transcripts(updated_records)
        recall_candidate_context = _get_structured_recall_ready_context(
            updated_records,
            previous_question=_get_question_answered_by_latest_record(
                updated_records
            ),
        )
        if recall_candidate_context.memory_candidates:
            used_clue_ids = _get_used_recall_clue_ids(recall_questions)
            if used_clue_ids:
                recall_candidate_context = _get_structured_recall_ready_context(
                    updated_records,
                    previous_question=_get_question_answered_by_latest_record(
                        updated_records
                    ),
                    used_clue_ids=used_clue_ids,
                )
        recall_ready_history = list(
            recall_candidate_context.conversation_history
        )

        if not recall_ready_history:
            _save_next_conversation_reply(
                record_id=record_id,
                session_id=session_id,
                candidate_count=len(transcripts),
                session_records=updated_records,
                latest_text=_get_current_transcript(
                    updated_records,
                    record_id,
                    transcript_text,
                ),
            )
            logger.info(
                "회상 가능한 memoryPoint가 아직 충분히 축적되지 않음: "
                "freeTalkCount=%s. 일반 질문 제공.",
                len(transcripts),
            )
            return

        current_turn_order = turn_position(updated_records, record_id)
        ask_after_turn = scheduled_recall_turn(
            current_turn_order, prior_wait_turns=recall_candidate_context.prior_wait_turns,
            prior_wait_seconds=recall_candidate_context.prior_wait_seconds,
        )
        latest_conversation_text = _get_current_transcript(
            updated_records,
            record_id,
            transcript_text,
        )
        recall_episode_metadata = _build_episode_metadata(
            session_id=session_id,
            record_id=record_id,
            session_records=updated_records,
            latest_text=latest_conversation_text,
            stage="READY_FOR_RECALL",
        )

        try:
            result = generate_and_save_recall_question(
                user_id=user_id,
                conversation_history=recall_ready_history,
                **session_recall_history(updated_records, recall_questions),
                memory_candidates=list(
                    recall_candidate_context.memory_candidates
                ),
                episode_id=recall_episode_metadata["episodeId"],
                recall_status="PENDING_RECALL",
                ask_after_turn=ask_after_turn,
                base_url=SPRING_BASE_URL,
            )
        except Exception as e:
            logger.warning("회상 질문 생성 또는 저장 실패. 일반 질문으로 복귀: %s", e)
            result = {
                "status": "SKIPPED",
                "reason": "회상 질문 생성 또는 저장 중 오류가 발생했습니다.",
            }

        reply_text = result.get("question")
        saved_question = result.get("savedQuestion")
        source_text = result.get("sourceText", "")

        if result.get("status") != "CREATED" or not reply_text:
            _save_next_conversation_reply(
                record_id=record_id,
                session_id=session_id,
                candidate_count=len(transcripts),
                session_records=updated_records,
                latest_text=_get_current_transcript(
                    updated_records,
                    record_id,
                    transcript_text,
                ),
            )
            logger.info("회상 질문 생성 실패 또는 SKIPPED. 일반 질문으로 대체: %s", result.get("reason"))
            return

        recall_question_id = (saved_question or {}).get("questionId")
        result_candidate = result.get("memoryCandidate")
        source_record_id = None

        if result_candidate is not None:
            matched_candidate = recall_candidate_context.find_memory_candidate(
                result_candidate
            )

            if (
                matched_candidate is None
                or _normalize_text(source_text)
                != _normalize_text(matched_candidate["sourceText"])
            ):
                logger.error(
                    "회상 질문의 구조화 memoryPoint 후보가 전달 후보와 달라 "
                    "일반 질문으로 복귀: candidate=%s",
                    result_candidate,
                )
                _save_next_conversation_reply(
                    record_id=record_id,
                    session_id=session_id,
                    candidate_count=len(transcripts),
                    session_records=updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
                )
                return

            source_record_id = matched_candidate["sourceRecordId"]

        if source_record_id is None:
            source_record_id = recall_candidate_context.find_source_record_id(
                source_text
            )

        if source_record_id is None:
            source_record_id = _find_memory_source_record_id(
                updated_records,
                source_text,
            )

        if not recall_question_id or source_record_id is None:
            logger.error(
                "회상 질문 또는 기준 레코드를 확인하지 못해 일반 질문으로 복귀: "
                "questionId=%s sourceText=%s",
                recall_question_id,
                source_text,
            )
            _save_next_conversation_reply(
                record_id=record_id,
                session_id=session_id,
                candidate_count=len(transcripts),
                session_records=updated_records,
                latest_text=_get_current_transcript(
                    updated_records,
                    record_id,
                    transcript_text,
                ),
            )
            return

        linked = _link_recall_question(
            record_id=source_record_id,
            recall_question_id=recall_question_id,
            answer_role="INITIAL",
        )

        if not linked:
            logger.error(
                "회상 기준 레코드 연결 실패로 회상 질문을 노출하지 않고 일반 질문으로 복귀: "
                "questionId=%s sourceRecordId=%s",
                recall_question_id,
                source_record_id,
            )
            _save_next_conversation_reply(
                record_id=record_id,
                session_id=session_id,
                candidate_count=len(transcripts),
                session_records=updated_records,
                latest_text=_get_current_transcript(
                    updated_records,
                    record_id,
                    transcript_text,
                ),
            )
            return

        if ask_after_turn <= current_turn_order:
            _save_ai_reply(record_id=record_id, reply_text=reply_text,
                           presented_recall_question_id=recall_question_id)
            logger.info("recall_presented reason=elapsed_memory_budget questionId=%s recordId=%s",
                        recall_question_id, record_id)
            return

        _save_next_conversation_reply(
            record_id=record_id,
            session_id=session_id,
            candidate_count=len(transcripts),
            session_records=updated_records,
            latest_text=_get_current_transcript(
                updated_records,
                record_id,
                transcript_text,
            ),
        )

        logger.info(
            "자연 회상 질문 저장 및 INITIAL 연결 완료. 질문은 지연 노출 대기: "
            "questionId=%s sourceRecordId=%s askAfterTurn=%s",
            recall_question_id,
            source_record_id,
            ask_after_turn,
        )

    except Exception as e:
        logger.exception(
            "process_voice_reply 파이프라인 처리 중 치명적 예외 발생: %s",
            str(e),
        )
    finally:
        logger.info("dialogue_processing_ms=%.1f recordId=%s sessionId=%s",
                    (perf_counter() - started_at) * 1000, record_id, session_id)
