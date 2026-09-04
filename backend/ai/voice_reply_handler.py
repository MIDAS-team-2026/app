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
    RecallTimingAction,
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
from recall.memory_event import (
    MemoryAnswerType,
    infer_answer_type,
    should_continue_memory_event,
)
from recall.memory_evidence_extractor import (
    extract_memory_clues,
    is_correction_utterance,
)
from recall.recall_api_client import analyze_session_recall

logger = logging.getLogger(__name__)
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


@dataclass(frozen=True)
class ConversationTurnState:
    latest_text: str
    conversation_history: tuple[str, ...]
    previous_question: str
    topic: str | None
    after_recall_answer: bool
    should_change_topic: bool
    is_memory_candidate: bool
    needs_memory_detail: bool
    has_real_content: bool
    has_anchorable_event: bool


@dataclass(frozen=True)
class RecallCandidateContext:
    conversation_history: tuple[str, ...] = ()
    source_record_ids: tuple[tuple[str, int], ...] = ()
    memory_candidates: tuple[dict, ...] = ()

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
    "최근에 시장이나 마트 이야기가 떠오른 적 있으세요?",
    "집 안에서 가장 오래 머무는 자리가 어디세요?",
    "요즘 하루 중 기다려지는 시간이 있으세요?",
]


# 고정 질문 답변 직후 자유대화로 넘어갈 때 쓰는 쿠션 문장.
# 고정 질문 답변 내용(성함/배우자/고향 등)과 무관하게, 방금 답변에
# 자연스럽게 반응하면서 자유대화로 전환만 시키는 범용 문장만 담는다.
FIXED_TO_FREE_TALK_OPENERS = [
    "좋습니다! 오늘은 어떤 이야기를 나눠볼까요?",
    "좋아요, 그럼 오늘 하루는 뭘 하면서 지내셨는지 이야기해주시겠어요?",
    "네, 알겠습니다. 이제 편하게 오늘 이야기를 나눠볼까요?",
    "좋습니다. 오늘 있었던 일 중에 편하게 나누고 싶은 이야기가 있으세요?",
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

# "아직 점심은 안 먹었어"처럼 아직 안 했거나 못 했거나 앞으로 할 일이라
# has_anchorable_event가 False인 답변에는 "그때 ~"처럼 이미 벌어진 일을
# 전제로 한 SAFE_STAGE_FALLBACK_QUESTIONS를 쓰면 안 된다. LLM이 문맥에 맞는
# 질문을 만들지 못했을 때만 쓰이는 최후 폴백이므로, 과거/시제를 특정하지
# 않는 범용 문구만 담는다.
NO_EVENT_FALLBACK_QUESTIONS = [
    "지금 기분은 좀 어떠세요?",
    "그럼 오늘은 어떻게 시간을 보내고 계세요?",
    "요즘 마음에 걸리는 다른 일이 있으세요?",
    "오늘 다른 이야기도 편하게 들려주시겠어요?",
]

TOPIC_CHANGE_ACKNOWLEDGEMENTS = [
    "아하, 그렇군요.",
    "그러셨군요.",
    "음, 그렇군요.",
]


RECALL_TRANSITION_ACKNOWLEDGEMENTS = [
    "아하, 그렇군요.",
    "그러셨군요.",
]


def _fetch_session_records(session_id: int) -> list[dict]:
    for attempt in range(2):
        try:
            resp = requests.get(
                f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
                timeout=10,
            )
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "세션 레코드 조회 재시도: sessionId=%s error=%s",
                    session_id,
                    e,
                )
                continue

            logger.error("세션 레코드 조회 실패: sessionId=%s, error=%s", session_id, e)

    return []


def _save_ai_reply(record_id: int, reply_text: str):
    payload = {
        "recordId": record_id,
        "replyText": reply_text,
    }

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


def _fetch_recall_questions(user_id: int) -> list[dict]:
    for attempt in range(2):
        try:
            resp = requests.get(
                f"{SPRING_BASE_URL}/api/recall/questions/{user_id}",
                timeout=10,
            )
            resp.raise_for_status()
            return resp.json()
        except Exception as e:
            if attempt == 0:
                logger.warning(
                    "회상 질문 조회 재시도: userId=%s error=%s",
                    user_id,
                    e,
                )
                continue

            logger.error("회상 질문 조회 실패: userId=%s error=%s", user_id, e)

    return []


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

    return {"doneToday": False, "onboardingDone": False}


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
    sorted_records = sorted(
        session_records,
        key=lambda record: int(record.get("recordId") or 0),
    )

    boundary_record_id = 0
    fixed_count = 0

    for record in sorted_records:
        role = str(record.get("answerRole") or "").upper()
        record_id = int(record.get("recordId") or 0)

        if role == "FIXED":
            fixed_count += 1
            boundary_record_id = max(boundary_record_id, record_id)
            continue

        if role in {"INITIAL", "RECALL"}:
            boundary_record_id = max(boundary_record_id, record_id)

    # 고정 질문이 0개인 세션은 오늘 고정 질문을 이미 마친 뒤 다시 시작한
    # 자유대화 세션이다. 온보딩 이후에는 하루 1개만 노출되므로 1개는 항상
    # "오늘의 고정 질문을 마치고 자유대화로 넘어간" 정상 상태다.
    # 2~4개가 있으면(온보딩 5개 순차 진행 중) 아직 고정 질문이 끝나지 않은 것이다.
    if 1 < fixed_count < len(FIXED_QUESTIONS):
        return []

    for record in sorted_records:
        role = str(record.get("answerRole") or "").upper()
        record_id = int(record.get("recordId") or 0)
        text = str(record.get("transcriptText") or "").strip()

        if record_id <= boundary_record_id:
            continue

        if not text:
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


_MEMORY_FOLLOWUP_REFERENCE_CUES = (
    "그때",
    "그곳",
    "같이",
    "함께",
    "다녀오",
    "그 음식",
    "그 방송",
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


def _build_memory_evidence_payloads(
    session_records: list[dict],
) -> list[dict]:
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

    for index, record in enumerate(sorted_records):
        record_id = int(record.get("recordId") or 0)

        if record_id not in candidate_ids:
            continue

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
                )
                and referenced_event_topic in {None, last_event_topic}
            ),
            has_conflicting_action=bool(
                question_actions
                and answer_actions
                and question_actions.isdisjoint(answer_actions)
            ),
            has_confirmed_clue=bool(question_clues),
            is_topic_transition=_is_memory_topic_transition(previous_question),
        )
        event_topic = (
            last_event_topic
            if continues_previous_event
            else (
                referenced_event_topic
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
        if (
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

        clues = extract_memory_clues(text, answer_type)
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


def _get_structured_recall_ready_context(
    session_records: list[dict],
    previous_question: str = "",
) -> RecallCandidateContext:
    payloads = _build_memory_evidence_payloads(session_records)

    if len(payloads) < 2:
        return RecallCandidateContext()

    matured_payloads = payloads[:-1]
    selection = build_memory_candidate_selection(
        matured_payloads,
        max_candidates=3,
    )
    latest_text = payloads[-1]["sourceText"]
    latest_evaluation = score_recall_memory_candidate(latest_text)
    transcripts = [payload["sourceText"] for payload in payloads]
    latest_topic = _detect_conversation_topic(
        latest_text,
        transcripts,
        previous_question,
    )
    timing = decide_recall_timing(
        matured_candidate_count=len(selection.candidates),
        latest_is_memory_candidate=bool(latest_evaluation["isValid"]),
        latest_has_followup_context=(
            latest_topic is not None
            and latest_topic not in {"LOW_INFO", "NEGATIVE"}
        ),
        latest_is_low_info=_is_low_info_response(latest_text),
        latest_is_negative=_is_negative_response(latest_text),
    )

    if timing.action != RecallTimingAction.ASK_RECALL:
        return RecallCandidateContext()

    selected_clues = tuple(
        (candidate, candidate.select_recall_clue())
        for candidate in selection.candidates
    )
    serialized_by_event_id = {
        candidate["eventId"]: candidate
        for candidate in selection.to_dict()["candidates"]
    }
    return RecallCandidateContext(
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
    normalized_question = _normalize_question_text(question_text)

    if not normalized_question:
        return False

    source_record_ids = [
        int(record.get("recordId") or 0)
        for record in session_records
        if (
            str(record.get("answerRole") or "").upper() == "INITIAL"
            and int(record.get("recallQuestionId") or 0) == int(recall_question_id)
        )
    ]
    boundary_record_id = min(source_record_ids) if source_record_ids else 0

    return any(
        int(record.get("recordId") or 0) >= boundary_record_id
        and int(record.get("recordId") or 0) != int(current_record_id)
        and _normalize_question_text(record.get("aiReplyText") or "").endswith(
            normalized_question
        )
        for record in session_records
    )


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
    text = _get_final_correction_segment(text)

    if _contains_any(text, QUALITATIVE_ABSENCE_PHRASES):
        return False

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



# "슬퍼 보였어"처럼 "-아/어 보이다"로 끝나는 관찰 서술은 화자 자신의 감정이
# 아니라 다른 사람·이야기 속 인물이 그렇게 "보였다"는 묘사인 경우가 많다
# (예: 영화 속 인물이 슬퍼 보였다는 감상). 이런 문장까지 사용자 본인의
# 부정적 감정으로 오판해 감정 케어 질문으로 빠지지 않도록 먼저 제거한다.
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
        ("ACTIVITY", ("하신 일", "하시던 일", "운동", "산책", "청소")),
        ("WEATHER", ("날씨", "더웠", "추웠", "바람", "햇빛")),
        ("PLACE", ("어디", "그곳", "장소", "다녀오신 곳")),
        ("PERSON", ("누구", "사람", "그분", "통화", "연락")),
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


def _get_stage_fallback_candidates(
    stage: str,
    latest_text: str,
    cycle_texts: list[str],
    has_anchorable_event: bool = True,
) -> list[str]:
    """주제와 무관하게, 지금 단계(DEEPEN/ANCHOR)에 쓸 수 있는 범용 폴백 질문을 고른다.

    실제 질문 문구는 generate_safe_followup_question(LLM)이 conversation_history
    전체를 보고 만든다 — 여기서 고르는 건 LLM 호출이 실패했을 때만 쓰이는
    최후 폴백이라, 주제별로 정교하게 나눌 필요가 없다. 예외가 둘 있다:
    "저정보 응답이 반복됨"과, has_anchorable_event=False("아직 안 먹었어" 등
    아직 벌어지지 않은/안 한 일이라 캐물을 사건이 없음) — 후자는 "그때 ~"
    전제인 SAFE_STAGE_FALLBACK_QUESTIONS 대신 시제 무관한 폴백을 쓴다.
    """
    is_low_info = _is_low_info_response(latest_text)

    if is_low_info and _count_recent_low_info_responses(
        _build_generation_history(cycle_texts, latest_text),
    ) >= 2:
        return REPEATED_LOW_INFO_QUESTIONS.get(stage, SAFE_STAGE_FALLBACK_QUESTIONS[stage])

    # "없어"류 저정보 응답은 has_anchorable_event 판정에서도 state_without_action으로
    # 걸리지만, 저정보 응답은 위의 반복 횟수 기준으로 이미 별도로 다루고 있으므로
    # (첫 번째는 SAFE_STAGE_FALLBACK_QUESTIONS, 반복되면 REPEATED_LOW_INFO_QUESTIONS)
    # 여기서 다시 NO_EVENT_FALLBACK_QUESTIONS로 덮어쓰지 않는다.
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
        "PERSON": ("보고 싶은 사람",),
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
    }
    markers = topic_markers.get(current_topic, ())

    if not markers:
        return _get_topic_openers()

    return [
        question
        for question in _get_topic_openers()
        if not _contains_any(question, markers)
    ]


def _get_contextual_topic_openers(
    current_topic: str | None,
    session_records: list[dict] | None,
    latest_text: str,
    candidates: list[str] | None = None,
) -> list[str]:
    candidates = list(candidates or _get_topic_change_openers(current_topic))
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
    cycle_texts = _get_cycle_transcripts(session_records)
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
    # "안녕"처럼 너무 짧아 실질적인 대화 내용이 없는 답변은(evaluate_memory_candidate가
    # too_short_or_meaningless로 걸러낸 경우) 아직 이어갈 이야기 자체가 없는 것이지,
    # "detail이 부족한 이야기"가 아니다. 이런 답변에는 ANCHOR("그때 ~")도,
    # 기존 맥락을 전제로 한 DEEPEN 이어가기도 부적절하므로 has_real_content로 구분해
    # 새 주제를 여는 흐름(OPEN_TOPIC)으로 보낸다.
    has_real_content = "too_short_or_meaningless" not in memory_evaluation["reasons"]
    # ANCHOR는 "이미 있는 이야기에서 구체적인 장면/사람/장소를 더 캐묻는" 단계라
    # "그때 ~"처럼 이미 벌어진 일이 있다는 걸 전제로 한다. "아직 점심은 안 먹었어"처럼
    # 아직 일어나지 않았거나(future_or_wish_expression) 안 했거나 못 했다고
    # 부정한(incomplete_or_negated_action) 답변, 확실치 않은 기억(uncertain_memory),
    # 행동 없이 상태만 있는 답변(state_without_action)은 "더 캐물을 사건" 자체가
    # 없으므로 ANCHOR로 보내면 "그때가 언제인데?" 같은 엉뚱한 질문이 나간다.
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
        latest_text=analyzed_text,
        conversation_history=tuple(conversation_history),
        previous_question=previous_question,
        topic=topic,
        after_recall_answer=_is_after_recall_answer(session_records),
        should_change_topic=should_change_topic,
        is_memory_candidate=bool(memory_evaluation["isValid"]),
        needs_memory_detail=(
            has_real_content
            and has_anchorable_event
            and topic != "PERSON"
            and not memory_evaluation["isValid"]
            and not _is_low_info_response(analyzed_text)
            and not _is_negative_response(analyzed_text)
        ),
        has_real_content=has_real_content,
        has_anchorable_event=has_anchorable_event,
    )


def _decide_next_conversation_action(
    state: ConversationTurnState,
    candidate_count: int,
) -> ConversationDecision:
    """다음 대화 액션을 정한다.

    has_followup_context를 더 이상 규칙 기반 주제 분류(state.topic) 결과로
    좁히지 않는다 — "이 문장이 무슨 주제인지" 우리가 못 알아챘다고 해서
    엉뚱한 새 주제로 강제 전환하지 않고, 실질적인 내용이 있는 답변이면
    FOLLOW_UP/DEEPEN으로 보내 generate_safe_followup_question(LLM)이 전체
    대화 맥락을 보고 판단하게 한다. "안녕"처럼 아직 이어갈 이야기 자체가
    없는 답변(state.has_real_content=False)만 OPEN_TOPIC으로 새로 연다.
    그 외에 진짜 새 주제를 열어야 하는 순간(회상 복귀, 부정 행동 표현,
    반복되는 저정보 응답)은 after_recall_answer/should_change_topic과
    _get_stage_fallback_candidates의 REPEATED_LOW_INFO_QUESTIONS,
    그리고 LLM 자신이 반환하는 shouldChangeTopic 신호가 담당한다.
    """
    return decide_conversation_action(
        candidate_count=candidate_count,
        after_recall_answer=state.after_recall_answer,
        should_change_topic=state.should_change_topic,
        needs_memory_detail=state.needs_memory_detail,
        has_followup_context=state.has_real_content,
    )


def _get_next_normal_question(
    candidate_count: int,
    session_records: list[dict] | None = None,
    latest_text: str = "",
) -> str:
    cycle_index = _count_completed_recall_answers(session_records)
    question_index = cycle_index + candidate_count
    used_questions = _get_recent_ai_replies(session_records)
    previous_questions = _get_recent_ai_reply_list(session_records)
    state = _analyze_conversation_turn(session_records, latest_text)
    followup_latest_text = state.latest_text
    followup_cycle_texts = list(state.conversation_history)
    followup_topic = state.topic
    after_recall_answer = state.after_recall_answer
    decision = _decide_next_conversation_action(state, candidate_count)

    if decision.action in {
        ConversationAction.OPEN_TOPIC,
        ConversationAction.RESUME_AFTER_RECALL,
    }:
        latest_record = _get_latest_record(session_records)
        latest_transcript = str(
            (latest_record or {}).get("transcriptText") or latest_text or ""
        )
        opening_candidates = (
            _get_after_recall_opening_candidates(latest_transcript)
            if after_recall_answer
            else _get_contextual_topic_openers(
                state.topic,
                session_records,
                state.latest_text,
            )
        )
        if after_recall_answer:
            opening_candidates = _get_contextual_topic_openers(
                state.topic,
                session_records,
                state.latest_text,
                opening_candidates,
            )
        opener_question = _pick_non_repeated_question(
            candidates=opening_candidates,
            used_questions=used_questions,
            previous_questions=previous_questions,
            start_index=0,
        )

        if opener_question:
            generated_opener = generate_safe_followup_question(
                conversation_history=_build_generation_history(
                    followup_cycle_texts,
                    followup_latest_text,
                ),
                stage="OPEN",
                fallback_question=opener_question,
                previous_questions=previous_questions,
            )["nextQuestion"]

            if after_recall_answer:
                return _with_recall_transition_acknowledgement(
                    generated_opener,
                    session_records,
                )

            return generated_opener

    if decision.action == ConversationAction.CHANGE_TOPIC:
        topic_change_openers = _get_contextual_topic_openers(
            followup_topic,
            session_records,
            followup_latest_text,
        )
        opener_question = _pick_non_repeated_question(
            candidates=topic_change_openers,
            used_questions=used_questions,
            previous_questions=previous_questions,
            start_index=0,
        )

        if opener_question:
            generated_opener = generate_safe_followup_question(
                conversation_history=_build_generation_history(
                    followup_cycle_texts,
                    followup_latest_text,
                ),
                stage="OPEN",
                fallback_question=opener_question,
                previous_questions=previous_questions,
            )["nextQuestion"]

            return _with_topic_change_acknowledgement(
                generated_opener,
                session_records,
            )

    stage = decision.stage
    fallback_candidates = _get_stage_fallback_candidates(
        stage,
        followup_latest_text,
        followup_cycle_texts,
        has_anchorable_event=state.has_anchorable_event,
    )
    fallback_question = _pick_non_repeated_question(
        candidates=fallback_candidates,
        used_questions=used_questions,
        previous_questions=previous_questions,
        start_index=question_index,
    )

    if fallback_question:
        generated_question = generate_safe_followup_question(
            conversation_history=_build_generation_history(
                followup_cycle_texts,
                followup_latest_text,
            ),
            stage=stage,
            fallback_question=fallback_question,
            previous_questions=previous_questions,
        )

        if generated_question.get("shouldChangeTopic"):
            topic_change_openers = _get_contextual_topic_openers(
                followup_topic,
                session_records,
                followup_latest_text,
            )
            opener_question = _pick_non_repeated_question(
                candidates=topic_change_openers,
                used_questions=used_questions,
                previous_questions=previous_questions,
                start_index=0,
            )

            if opener_question:
                generated_opener = generate_safe_followup_question(
                    conversation_history=_build_generation_history(
                        followup_cycle_texts,
                        followup_latest_text,
                    ),
                    stage="OPEN",
                    fallback_question=opener_question,
                    previous_questions=previous_questions,
                )["nextQuestion"]

                return _with_topic_change_acknowledgement(
                    generated_opener,
                    session_records,
                )

        return generated_question["nextQuestion"]

    opener_question = _pick_non_repeated_question(
        candidates=_get_contextual_topic_openers(
            followup_topic,
            session_records,
            followup_latest_text,
        ),
        used_questions=used_questions,
        previous_questions=previous_questions,
        start_index=0,
    )

    if opener_question:
        return opener_question

    return _pick_least_recent_question(
        candidates=_get_topic_openers(),
        previous_questions=previous_questions,
        start_index=question_index,
    )


def _get_current_transcript(
    session_records: list[dict],
    current_record_id: int,
    fallback_text: str = "",
) -> str:
    for record in session_records:
        if record.get("recordId") == current_record_id:
            return record.get("transcriptText") or fallback_text

    return fallback_text


def _get_record_by_id(
    session_records: list[dict],
    record_id: int,
) -> dict | None:
    for record in session_records:
        if record.get("recordId") == record_id:
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
) -> int | None:
    """
    아직 답변되지 않은 회상 질문을 찾는다.
    INITIAL은 회상 질문의 기준 답변이고,
    같은 recallQuestionId를 가진 RECALL이 아직 없으면 현재 답변을 RECALL로 연결한다.
    """
    initial_question_ids = []
    recalled_question_ids = set()

    sorted_records = sorted(
        session_records,
        key=lambda record: int(record.get("recordId") or 0),
    )

    for record in sorted_records:
        role = str(record.get("answerRole") or "").upper()
        question_id = record.get("recallQuestionId")
        record_id = record.get("recordId")

        if question_id is None:
            continue

        question_id = int(question_id)

        if role == "RECALL":
            recalled_question_ids.add(question_id)

        if role == "INITIAL" and record_id != current_record_id:
            initial_question_ids.append(question_id)

    for question_id in reversed(initial_question_ids):
        if question_id not in recalled_question_ids:
            return question_id

    return None


def process_voice_reply(
    record_id: int,
    session_id: int,
    user_id: int,
    transcript_text: str = "",
    speech_risk_score: float = 0.0,
    run_realtime_analysis: bool = False,
):
    logger.info("AI 답변 및 매핑 시작: recordId=%s sessionId=%s", record_id, session_id)

    try:
        session_records = _fetch_session_records(session_id)

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

        if current_answer_role == "INITIAL":
            current_question_id = (current_record or {}).get("recallQuestionId")
            question_text = (
                _find_recall_question_text(user_id, int(current_question_id))
                if current_question_id is not None
                else ""
            )

            if not question_text:
                logger.error(
                    "연결된 회상 질문 문구를 찾지 못해 복구 중단: recordId=%s questionId=%s",
                    record_id,
                    current_question_id,
                )
                return

            _save_ai_reply(record_id=record_id, reply_text=question_text)
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
            _mark_fixed_questions_done_today(
                user_id,
                onboarding=not fixed_status["onboardingDone"],
            )
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_fixed_to_free_talk_opener(session_records),
            )
            logger.info("고정 답변 이후 자유대화 질문 저장 재개: recordId=%s", record_id)
            return

        # 1. 직전 회상 질문에 대한 답변이면 RECALL로 연결하고 자유대화로 복귀한다.
        pending_recall_question_id = _find_pending_recall_question_id(
            session_records=session_records,
            current_record_id=record_id,
        )

        if pending_recall_question_id is not None:
            pending_question_text = _find_recall_question_text(
                user_id,
                pending_recall_question_id,
            )

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
                )
                logger.warning(
                    "표시되지 않은 회상 질문을 먼저 복구하고 현재 답변의 RECALL 연결은 보류: "
                    "recordId=%s questionId=%s",
                    record_id,
                    pending_recall_question_id,
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

            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    0,
                    records_after_recall,
                    latest_text=_get_current_transcript(
                        records_after_recall,
                        record_id,
                        transcript_text,
                    ),
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
            _mark_fixed_questions_done_today(user_id, onboarding=False)

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
                _mark_fixed_questions_done_today(user_id, onboarding=True)

                _save_ai_reply(
                    record_id=record_id,
                    reply_text=_get_fixed_to_free_talk_opener(session_records),
                )
                return

        # 3. 충분히 축적되고 한 턴 이상 지난 memoryPoint가 있으면 회상 질문 생성
        updated_records = _fetch_session_records(session_id)

        if not updated_records:
            updated_records = session_records

        transcripts = _extract_recall_candidate_transcripts(updated_records)
        recall_candidate_context = _get_structured_recall_ready_context(
            updated_records,
            previous_question=_get_question_answered_by_latest_record(
                updated_records
            ),
        )
        recall_ready_history = list(
            recall_candidate_context.conversation_history
        )

        if not recall_ready_history:
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    len(transcripts),
                    updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
                ),
            )
            logger.info(
                "회상 가능한 memoryPoint가 아직 충분히 축적되지 않음: "
                "freeTalkCount=%s. 일반 질문 제공.",
                len(transcripts),
            )
            return

        try:
            result = generate_and_save_recall_question(
                user_id=user_id,
                conversation_history=recall_ready_history,
                memory_candidates=list(
                    recall_candidate_context.memory_candidates
                ),
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
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    len(transcripts),
                    updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
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
                _save_ai_reply(
                    record_id=record_id,
                    reply_text=_get_next_normal_question(
                        len(transcripts),
                        updated_records,
                        latest_text=_get_current_transcript(
                            updated_records,
                            record_id,
                            transcript_text,
                        ),
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
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    len(transcripts),
                    updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
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
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    len(transcripts),
                    updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
                ),
            )
            return

        _save_ai_reply(
            record_id=record_id,
            reply_text=_with_recall_question_acknowledgement(
                reply_text,
                updated_records,
            ),
        )

        logger.info(
            "자연 회상 질문 저장 및 INITIAL 연결 완료: questionId=%s sourceRecordId=%s",
            recall_question_id,
            source_record_id,
        )

    except Exception as e:
        logger.exception(
            "process_voice_reply 파이프라인 처리 중 치명적 예외 발생: %s",
            str(e),
        )
