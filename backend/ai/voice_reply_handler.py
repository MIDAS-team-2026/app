"""
음성 업로드 후 AI 답변 생성 및 회상 질문 저장 로직.
STT는 Spring STTService가 업로드 시점에 처리하므로,
이 모듈은 Spring API에서 transcriptText를 조회해 사용한다.

흐름:
1. 사용자는 모든 답변을 음성으로 한다.
2. Spring이 음성 업로드, S3 저장, STT 저장을 처리한다.
3. 이 모듈은 STT 결과를 기반으로 다음 AI 질문 텍스트를 생성한다.
4. 앱은 aiReplyText를 읽어 사용자에게 음성 안내/TTS로 제공한다.

초기 5문항:
- answerRole = FIXED
- questionType = PERSONAL / ORIENTATION
- category = INITIAL_FIXED

자연 회상 질문:
- answerRole = INITIAL / RECALL
- questionType = RECALL
- category = CONVERSATION
"""

import logging
import os
import requests

from recall.conversation_recall_generator import (
    generate_and_save_recall_question,
)
from recall.recall_api_client import analyze_session_recall

logger = logging.getLogger(__name__)
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


FIXED_QUESTIONS = [
    {
        "questionText": "성함이 어떻게 되시나요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "USER_NAME",
    },
    {
        "questionText": "생년월일이 어떻게 되시나요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "BIRTH_DATE",
    },
    {
        "questionText": "배우자분이나 자녀분 성함 중 한 분을 말씀해주실 수 있나요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "FAMILY_NAME",
    },
    {
        "questionText": "오늘은 무슨 요일인가요?",
        "questionType": "ORIENTATION",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "TODAY_WEEKDAY",
    },
    {
        "questionText": "오늘 날짜가 어떻게 되나요?",
        "questionType": "ORIENTATION",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "TODAY_DATE",
    },
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
        logger.error(
            "세션 레코드 실시간 동기화 조회 실패: sessionId=%s, 에러=%s",
            session_id,
            e,
        )
        return []


def _save_ai_reply(record_id: int, reply_text: str):
    """
    생성된 AI 오디오 가이드 텍스트 답변을 Spring DB에 저장한다.
    앱은 이 aiReplyText를 읽어 사용자에게 음성 안내/TTS로 제공한다.
    """
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
            "-> 성공적으로 레코드에 질문 ID 주입 완료: recordId=%d, questionId=%d, role=%s",
            record_id,
            recall_question_id,
            answer_role,
        )

    except Exception as e:
        logger.error(
            "-> 레코드 질문 ID 주입(Link) API 통신 실패: recordId=%d, 에러=%s",
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
        logger.error("회상 질문 조회 실패: userId=%s, 에러=%s", user_id, e)
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


def _extract_transcripts(session_records: list[dict]) -> list[str]:
    transcripts = []

    for record in session_records:
        text = str(record.get("transcriptText") or "").strip()

        if text:
            transcripts.append(text)

    return transcripts


def _count_fixed_answers(session_records: list[dict]) -> int:
    count = 0

    for record in session_records:
        role = str(record.get("answerRole") or "").upper()

        if role == "FIXED":
            count += 1

    return count


def _find_pending_recall_question_id(
    session_records: list[dict],
    current_record_id: int,
) -> int | None:
    """
    자연 회상 질문 INITIAL은 있는데,
    아직 같은 recallQuestionId의 RECALL 답변이 없는 경우를 찾는다.
    """

    initial_question_ids = []
    recalled_question_ids = set()

    for record in session_records:
        role = str(record.get("answerRole") or "").upper()
        question_id = record.get("recallQuestionId")
        record_id = record.get("recordId")

        if question_id is None:
            continue

        if role == "RECALL":
            recalled_question_ids.add(question_id)

        if role == "INITIAL" and record_id != current_record_id:
            initial_question_ids.append(question_id)

    for question_id in reversed(initial_question_ids):
        if question_id not in recalled_question_ids:
            return int(question_id)

    return None


def process_voice_reply(
    record_id: int,
    session_id: int,
    user_id: int,
    transcript_text: str = "",
):
    logger.info(
        "AI 답변 및 매핑 시작: recordId=%s sessionId=%s",
        record_id,
        session_id,
    )

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

        # 1. 자연 회상 질문에 대한 사용자 답변이면 RECALL로 연결
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
                "회상 질문 답변으로 매핑 완료: recordId=%s, questionId=%s",
                record_id,
                pending_recall_question_id,
            )

            try:
                analyze_session_recall(
                    user_id=user_id,
                    session_id=session_id,
                    speech_risk_score=0.0,
                    base_url=SPRING_BASE_URL,
                )
            except Exception as e:
                logger.warning("실시간 회상 분석 건너뜀: %s", e)

            # return

        # 2. 초기 고정 질문 5개 처리
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
                next_question_text = FIXED_QUESTIONS[next_index]["questionText"]

                _save_ai_reply(
                    record_id=record_id,
                    reply_text=next_question_text,
                )

                logger.info(
                    "초기 고정 질문 제공 완료: nextIndex=%s",
                    next_index,
                )
                return

            logger.info("초기 고정 질문 5개 완료. 자연 회상 질문 단계로 전환.")

        # 3. 초기 5문항 이후 자연 회상 질문 생성
        updated_records = _fetch_session_records(session_id)

        if not updated_records:
            updated_records = session_records

        transcripts = _extract_transcripts(updated_records)

        if not transcripts and transcript_text:
            transcripts = [transcript_text]

        result = generate_and_save_recall_question(
            user_id=user_id,
            conversation_history=transcripts,
            base_url=SPRING_BASE_URL,
        )

        reply_text = result.get("question")
        saved_question = result.get("savedQuestion")
        memory_point = result.get("memoryPoint", "")

        if reply_text:
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
                    "자연 회상 질문 저장 및 INITIAL 연결 완료: questionId=%s, memoryPoint=%s",
                    recall_question_id,
                    memory_point,
                )

    except Exception as e:
        logger.exception(
            "process_voice_reply 파이프라인 처리 중 치명적 예외 발생: %s",
            str(e),
        )