"""
음성 업로드 후 AI 답변 생성 및 회상 질문 저장 로직.

핵심 흐름:
1. 앱 첫 화면은 사용자가 먼저 말하는 구조이므로 첫 발화는 채점하지 않고 첫 고정 질문을 안내한다.
2. 초기 고정 질문은 FIXED로 저장한다.
3. FIXED / INITIAL / RECALL 답변은 새로운 memoryPoint 후보에서 제외한다.
4. 자유대화 답변이 3개 이상 쌓이면 회상 질문을 생성한다.
5. 회상 질문 답변은 RECALL로 저장하고, 이후 다시 자유대화로 복귀한다.
"""

import logging
import os
import requests

from recall.free_talk_question_generator import (
    generate_safe_followup_question,
    is_similar_to_previous_question,
)
from recall.conversation_recall_generator import generate_and_save_recall_question
from recall.recall_api_client import analyze_session_recall

logger = logging.getLogger(__name__)
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


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
        "questionText": "오늘 날짜도 기억나세요?",
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


AFTER_RECALL_OPENING_QUESTIONS = [
    "이어서 오늘 드신 것 중에 생각나는 음식이 있으세요?",
    "이번에는 오늘 집에서 하신 일 중에 하나 말씀해주실래요?",
    "이번에는 최근에 본 방송이나 들은 노래 이야기도 해볼까요?",
    "이어서 오늘 밖이나 창밖에서 본 것이 있으세요?",
    "이번에는 손에 잡았던 물건이나 하셨던 일이 있으세요?",
    "오늘 하루 중 편하게 이야기하고 싶은 일이 있으세요?",
]

AFTER_LOW_INFO_RECALL_OPENING_QUESTIONS = [
    "괜찮습니다. 오늘 집에서 하신 일 중에 편하게 떠오르는 게 있으세요?",
    "괜찮습니다. 이번에는 오늘 보신 방송이나 들은 소리 이야기를 해볼까요?",
    "괜찮습니다. 그러면 오늘 드신 것 중에 기억나는 음식이 있으세요?",
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
        "괜찮습니다. 그럼 다른 이야기로 해볼까요? 오늘 편하게 떠오르는 일이 있으세요?",
        "괜찮습니다. 그럼 오늘 집에서 하신 일이나 보신 것 중 편한 것부터 말씀해주실래요?",
        "알겠습니다. 그럼 오늘 하루 중 가장 편했던 순간이 있으세요?",
    ],
    "ANCHOR": [
        "괜찮습니다. 오늘 떠올리기 쉬운 일 하나만 말씀해주실래요?",
        "괜찮습니다. 오늘 기억나는 음식이나 방송 중 편한 것부터 이야기해볼까요?",
        "알겠습니다. 지금 편하게 생각나는 일이 하나 있으세요?",
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

TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS = {
    "LOW_INFO": {
        "DEEPEN": [
            "아하, 그러셨군요. 그럼 오늘 드신 것 중에 기억나는 음식이 있으세요?",
            "그러셨군요. 오늘 집에서 하신 일 중에 하나만 떠오르세요?",
            "음, 그렇군요. 오늘 보신 것 중에 기억나는 장면이 있으세요?",
        ],
        "ANCHOR": [
            "오늘 하루에서 가장 먼저 떠오르는 일이 있으세요?",
            "오늘 집에서 하신 일 중에 기억나는 게 하나 있으세요?",
            "방금 이야기 말고 오늘 있었던 일 중에 생각나는 게 있으세요?",
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
    "HEALTH": {
        "DEEPEN": [
            "약은 언제쯤 드셨어요?",
            "약 드신 뒤에는 몸이 좀 어떠셨어요?",
            "병원에는 혼자 다녀오셨어요, 아니면 누군가와 함께 가셨어요?",
            "그때 몸 상태는 어떠셨어요?",
            "어느 쪽이 제일 불편하셨어요?",
            "지금은 조금 괜찮으세요?",
            "병원에서 기다리거나 진료받을 때 기억나는 게 있으세요?",
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
    "MEDIA": {
        "DEEPEN": [
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
            "오늘 하신 일 중 나중에 기억할 만한 장면이 있으세요?",
            "그 일을 떠올리면 제일 먼저 생각나는 게 있으세요?",
            "다시 이야기한다면 어떤 말로 떠올리면 좋을까요?",
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
    try:
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.error("세션 레코드 조회 실패: sessionId=%s, error=%s", session_id, e)
        return []


def _save_ai_reply(record_id: int, reply_text: str):
    payload = {
        "recordId": record_id,
        "replyText": reply_text,
    }

    resp = requests.post(
        f"{SPRING_BASE_URL}/api/voice/reply",
        json=payload,
        timeout=10,
    )
    resp.raise_for_status()


def _link_recall_question(
    record_id: int,
    recall_question_id: int,
    answer_role: str,
):
    try:
        payload = {
            "recordId": record_id,
            "recallQuestionId": recall_question_id,
            "answerRole": answer_role,
        }

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

    except Exception as e:
        logger.error(
            "레코드 질문 연결 실패: recordId=%s error=%s",
            record_id,
            e,
        )


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
    try:
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/recall/questions/{user_id}",
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
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


def _count_fixed_answers(session_records: list[dict]) -> int:
    return sum(
        1
        for record in session_records
        if str(record.get("answerRole") or "").upper() == "FIXED"
    )


def _extract_recall_candidate_transcripts(session_records: list[dict]) -> list[str]:
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
    transcripts = []
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

    if fixed_count < len(FIXED_QUESTIONS):
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

        transcripts.append(text)

    return transcripts


def _normalize_question_text(text: str) -> str:
    return " ".join(str(text or "").split())


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


def _contains_any(text: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword in text for keyword in keywords)


QUALITATIVE_ABSENCE_PHRASES = (
    "맛이 없",
    "맛없",
    "재미없",
    "재미 없",
    "기운이 없",
    "입맛이 없",
)


def _is_low_info_response(text: str) -> bool:
    if _contains_any(text, QUALITATIVE_ABSENCE_PHRASES):
        return False

    return _contains_any(
        text,
        (
            "몰라",
            "모르",
            "기억 안",
            "생각 안",
            "없어",
            "없다",
            "없었",
            "없네",
            "없다니까",
        ),
    )


def _is_negative_response(text: str) -> bool:
    return _contains_any(
        text,
        (
            "별로",
            "싫",
            "힘들",
            "우울",
            "속상",
            "걱정",
            "불편",
            "무거웠",
            "화가",
            "화났",
            "짜증",
        ),
    )


def _is_short_response(text: str) -> bool:
    return len(_normalize_text(text)) <= 4


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
        "먹고 싶었던 음식 중에 나중에 기억할 만한 게 있으세요?",
        "그 음식을 떠올리면 제일 먼저 생각나는 모습이 있으세요?",
        "그 음식 이야기를 다시 한다면 어떤 말이 먼저 떠오를까요?",
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


def _detect_topic_in_text(text: str) -> str | None:
    health_text = text.replace("약속", "")

    if _is_low_info_response(health_text):
        return "LOW_INFO"

    if _contains_any(
        health_text,
        (
            "약",
            "병원",
            "진료",
            "의사",
            "간호",
            "아프",
            "아팠",
            "다쳤",
            "몸",
            "허리",
            "검사",
            "팔",
            "다리",
            "무릎",
            "어깨",
            "배",
            "머리",
            "눈이 아",
        ),
    ):
        return "HEALTH"

    if _contains_any(
        text,
        (
            "별로",
            "싫",
            "힘들",
            "우울",
            "속상",
            "걱정",
            "불편",
            "무거웠",
            "화가",
            "화났",
            "짜증",
            "슬프",
            "슬펐",
            "외로",
            "무서",
            "불안",
            "서운",
        ),
    ):
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

    if _contains_any(
        text,
        (
            "샀",
            "사왔",
            "장 봤",
            "장봤",
            "장 보",
            "장보",
            "장보러",
            "물건",
        ),
    ):
        return "SHOPPING"

    if _contains_any(
        health_text,
        (
            "약",
            "병원",
            "진료",
            "의사",
            "간호",
            "아프",
            "아팠",
            "다쳤",
            "몸",
            "허리",
            "불편",
            "검사",
        ),
    ):
        return "HEALTH"

    if _contains_any(
        text,
        (
            "먹",
            "마시",
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
            "맛",
        ),
    ):
        return "FOOD"

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
            "사람",
            "연락",
            "통화",
            "만났",
        ),
    ):
        return "PERSON"

    if _contains_any(
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
            "다녀",
            "갔",
            "갔다",
        ),
    ):
        return "PLACE"

    if _contains_any(
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
        ),
    ):
        return "MEDIA"

    return None


def _detect_recent_context_topic(cycle_texts: list[str]) -> str | None:
    for text in reversed(cycle_texts):
        topic = _detect_topic_in_text(text)

        if topic is not None:
            return topic

    return None


def _detect_conversation_topic(latest_text: str, cycle_texts: list[str]) -> str | None:
    # 최신 답변의 주제를 우선한다. 이전 답변까지 먼저 섞으면
    # 사용자가 새 주제로 넘어갔는데도 이전 주제 질문이 계속 나올 수 있다.
    latest_topic = _detect_topic_in_text(latest_text)
    context_topic = _detect_recent_context_topic(cycle_texts)
    previous_cycle_texts = list(cycle_texts)

    if previous_cycle_texts and previous_cycle_texts[-1].strip() == latest_text.strip():
        previous_cycle_texts = previous_cycle_texts[:-1]

    previous_context_topic = _detect_recent_context_topic(previous_cycle_texts)

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

    if latest_topic is not None:
        return latest_topic

    if context_topic is not None:
        return context_topic

    return None


def _get_topic_aware_fallback_candidates(
    stage: str,
    latest_text: str,
    cycle_texts: list[str],
) -> list[str]:
    topic = _detect_conversation_topic(latest_text, cycle_texts)

    if topic is None:
        return []

    candidates = TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS.get(topic, {}).get(stage, [])

    if topic == "LOW_INFO" and _count_recent_low_info_responses(cycle_texts) >= 2:
        candidates = REPEATED_LOW_INFO_QUESTIONS.get(stage, candidates)

    if topic == "FOOD":
        if _contains_any(latest_text, ("입맛", "밥맛")):
            candidates = FOOD_APPETITE_QUESTIONS.get(stage, candidates)
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

        if _contains_any(latest_text, ("맛있", "맛은", "맛이", "시원")):
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

    if topic == "HEALTH":
        context = " ".join([latest_text, *cycle_texts])

        if "약" not in context:
            candidates = [
                question
                for question in candidates
                if "약" not in question
            ]

        if "병원" not in context:
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

        if not _contains_any(context, ("노래", "가수", "들었")):
            candidates = [
                question
                for question in candidates
                if "노래" not in question and "들을 때" not in question
            ]

        if _contains_any(latest_text, ("재미없", "재미 없", "별로")):
            candidates = [
                question
                for question in candidates
                if "사람" not in question
            ]

        if _contains_any(context, ("노래", "가수", "들었")):
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

        if _contains_any(latest_text, ("좋았", "재밌", "즐거")):
            candidates = [
                question
                for question in candidates
                if "기분" not in question
            ]

    return candidates


def _get_topic_openers() -> list[str]:
    return SAFE_OPENING_QUESTIONS


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

    for offset in range(len(candidates)):
        question = candidates[(start_index + offset) % len(candidates)]

        if _normalize_text(question) not in used_questions:
            return question

    return None


def _with_topic_change_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
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


def _is_after_recall_answer(session_records: list[dict] | None) -> bool:
    latest_record = _get_latest_record(session_records)

    if not latest_record:
        return False

    return str(latest_record.get("answerRole") or "").upper() == "RECALL"


def _with_recall_transition_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
    if _contains_any(question, ("괜찮습니다.", "그러셨군요.", "그렇군요.", "알겠습니다.", "그랬군요.")):
        return question

    index = len(session_records or []) % len(RECALL_TRANSITION_ACKNOWLEDGEMENTS)
    acknowledgement = RECALL_TRANSITION_ACKNOWLEDGEMENTS[index]
    return f"{acknowledgement} {question}"


def _get_after_recall_opening_candidates(recall_answer_text: str) -> list[str]:
    if _is_low_info_response(recall_answer_text):
        return AFTER_LOW_INFO_RECALL_OPENING_QUESTIONS

    if _is_negative_response(recall_answer_text):
        return AFTER_NEGATIVE_RECALL_OPENING_QUESTIONS

    if _is_short_response(recall_answer_text):
        return AFTER_SHORT_RECALL_OPENING_QUESTIONS

    return AFTER_RECALL_OPENING_QUESTIONS


def _get_next_normal_question(
    candidate_count: int,
    session_records: list[dict] | None = None,
    latest_text: str = "",
) -> str:
    cycle_index = _count_completed_recall_answers(session_records)
    question_index = cycle_index + candidate_count
    used_questions = _get_recent_ai_replies(session_records)
    previous_questions = _get_recent_ai_reply_list(session_records)
    recent_context = previous_questions + _get_recent_transcript_list(session_records)
    cycle_texts = _get_cycle_transcripts(session_records)

    if candidate_count <= 0:
        after_recall_answer = _is_after_recall_answer(session_records)
        latest_record = _get_latest_record(session_records)
        latest_transcript = str(
            (latest_record or {}).get("transcriptText") or latest_text or ""
        )
        opening_candidates = (
            _get_after_recall_opening_candidates(latest_transcript)
            if after_recall_answer
            else _get_topic_openers()
        )
        opener_question = _pick_non_repeated_question(
            candidates=opening_candidates,
            used_questions=used_questions,
            previous_questions=recent_context,
            start_index=cycle_index,
        )

        if opener_question:
            if after_recall_answer:
                return _with_recall_transition_acknowledgement(
                    opener_question,
                    session_records,
                )

            return opener_question

    stage = "DEEPEN" if candidate_count <= 2 else "ANCHOR"
    topic_aware_candidates = _get_topic_aware_fallback_candidates(
        stage,
        latest_text,
        cycle_texts,
    )

    if (
        _is_low_info_response(latest_text)
        and _count_recent_low_info_responses(
            _get_recent_transcript_list(session_records),
        )
        >= 2
    ):
        topic_aware_candidates = REPEATED_LOW_INFO_QUESTIONS.get(
            stage,
            topic_aware_candidates,
        )

    fallback_candidates = topic_aware_candidates or SAFE_STAGE_FALLBACK_QUESTIONS[stage]
    fallback_question = _pick_non_repeated_question(
        candidates=fallback_candidates,
        used_questions=used_questions,
        previous_questions=recent_context,
        start_index=question_index,
    )

    if fallback_question:
        generated_question = generate_safe_followup_question(
            conversation_history=cycle_texts + [latest_text],
            stage=stage,
            fallback_question=fallback_question,
            previous_questions=recent_context,
        )

        if generated_question.get("shouldChangeTopic"):
            opener_question = _pick_non_repeated_question(
                candidates=_get_topic_openers(),
                used_questions=used_questions,
                previous_questions=recent_context,
                start_index=cycle_index + 1,
            )

            if opener_question:
                return _with_topic_change_acknowledgement(
                    opener_question,
                    session_records,
                )

        return generated_question["nextQuestion"]

    opener_question = _pick_non_repeated_question(
        candidates=_get_topic_openers(),
        used_questions=used_questions,
        previous_questions=recent_context,
        start_index=question_index,
    )

    if opener_question:
        return opener_question

    return _get_topic_openers()[question_index % len(_get_topic_openers())]


def _get_current_transcript(
    session_records: list[dict],
    current_record_id: int,
    fallback_text: str = "",
) -> str:
    for record in session_records:
        if record.get("recordId") == current_record_id:
            return record.get("transcriptText") or fallback_text

    return fallback_text


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


def _is_first_user_turn(session_records: list[dict], current_record_id: int) -> bool:
    """
    앱 첫 화면은 AI가 먼저 질문하지 않고 사용자가 먼저 말하는 구조다.
    그래서 첫 발화는 FIXED 답변으로 찍지 않고 첫 고정 질문을 안내한다.
    """
    if len(session_records) != 1:
        return False

    only_record = session_records[0]
    return only_record.get("recordId") == current_record_id


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
            session_records = [
                {
                    "recordId": record_id,
                    "transcriptText": transcript_text,
                    "answerRole": None,
                    "recallQuestionId": None,
                    "parentRecordId": None,
                }
            ]

        # 0. 세션 첫 발화는 답변으로 채점하지 않고 첫 질문만 제공한다.
        if _is_first_user_turn(session_records, record_id):
            _save_ai_reply(
                record_id=record_id,
                reply_text=FIXED_QUESTIONS[0]["questionText"],
            )
            logger.info("첫 사용자 발화 감지. 첫 고정 질문 제공.")
            return

        # 1. 직전 회상 질문에 대한 답변이면 RECALL로 연결하고 자유대화로 복귀한다.
        pending_recall_question_id = _find_pending_recall_question_id(
            session_records=session_records,
            current_record_id=record_id,
        )

        if pending_recall_question_id is not None:
            _link_recall_question(
                record_id=record_id,
                recall_question_id=pending_recall_question_id,
                answer_role="RECALL",
            )

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

        # 2. 초기 고정 질문 답변 처리
        fixed_answer_count = _count_fixed_answers(session_records)

        if fixed_answer_count < len(FIXED_QUESTIONS):
            current_question = FIXED_QUESTIONS[fixed_answer_count]

            fixed_question = _find_or_create_fixed_question(
                user_id=user_id,
                question_data=current_question,
            )

            fixed_question_id = fixed_question.get("questionId") if fixed_question else None

            if fixed_question_id:
                _link_recall_question(
                    record_id=record_id,
                    recall_question_id=fixed_question_id,
                    answer_role="FIXED",
                )

            next_index = fixed_answer_count + 1

            if next_index < len(FIXED_QUESTIONS):
                _save_ai_reply(
                    record_id=record_id,
                    reply_text=FIXED_QUESTIONS[next_index]["questionText"],
                )

                logger.info("초기 고정 질문 제공 완료: nextIndex=%s", next_index)
                return

            logger.info("초기 고정 질문 5개 완료. 자유대화 단계로 전환.")

            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(0, session_records),
            )
            return

        # 3. 자유대화 답변 3턴 이상 쌓이면 회상 질문 생성
        updated_records = _fetch_session_records(session_id)

        if not updated_records:
            updated_records = session_records

        transcripts = _extract_recall_candidate_transcripts(updated_records)

        if len(transcripts) < 3:
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
            logger.info("자유대화 후보 부족: count=%s. 일반 질문 제공.", len(transcripts))
            return

        result = generate_and_save_recall_question(
            user_id=user_id,
            conversation_history=transcripts,
            base_url=SPRING_BASE_URL,
        )

        reply_text = result.get("question")
        saved_question = result.get("savedQuestion")
        memory_point = result.get("memoryPoint", "")

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

        _save_ai_reply(
            record_id=record_id,
            reply_text=reply_text,
        )

        if saved_question:
            recall_question_id = saved_question.get("questionId")

            if recall_question_id:
                _link_recall_question(
                    record_id=record_id,
                    recall_question_id=recall_question_id,
                    answer_role="INITIAL",
                )

                logger.info(
                    "자연 회상 질문 저장 및 INITIAL 연결 완료: questionId=%s memoryPoint=%s",
                    recall_question_id,
                    memory_point,
                )

    except Exception as e:
        logger.exception(
            "process_voice_reply 파이프라인 처리 중 치명적 예외 발생: %s",
            str(e),
        )
