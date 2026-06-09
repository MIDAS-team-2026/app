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


NORMAL_FOLLOW_UP_QUESTIONS = [
    "오늘은 어떤 하루 보내셨어요?",
    "오늘 식사는 어떻게 하셨어요?",
    "오늘 어디 다녀오신 곳 있으세요?",
    "오늘 누구와 이야기 나누셨어요?",
    "요즘 기억에 남는 일이 있으세요?",
    "오늘 기분은 어떠셨어요?",
    "오늘 가장 편안했던 순간이 있으셨어요?",
    "요즘 자주 떠오르는 사람이 있으세요?",
    "최근에 드시고 싶었던 음식이 있으세요?",
    "오늘 집에서 주로 무엇을 하셨어요?",
    "요즘 즐겨 보시는 방송이나 노래가 있으세요?",
    "오늘 밖이나 창밖에서 기억나는 풍경이 있으세요?",
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


def _get_next_normal_question(
    candidate_count: int,
    session_records: list[dict] | None = None,
) -> str:
    cycle_index = _count_completed_recall_answers(session_records)
    question_index = (cycle_index * 3 + candidate_count) % len(NORMAL_FOLLOW_UP_QUESTIONS)
    return NORMAL_FOLLOW_UP_QUESTIONS[question_index]


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

            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(0, session_records),
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
                reply_text="오늘은 어떤 하루 보내셨어요?",
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
                reply_text=_get_next_normal_question(len(transcripts), updated_records),
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
                reply_text=_get_next_normal_question(len(transcripts), updated_records),
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
