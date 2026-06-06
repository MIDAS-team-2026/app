"""
음성 업로드 후 AI 답변 생성 및 회상 질문 저장 로직.

STT는 Spring STTService가 업로드 시점에 처리하므로,
이 모듈은 Spring API에서 transcriptText를 조회해 사용한다.
"""

import logging
import os
import time

import requests

from recall.conversation_recall_generator import (
    generate_recall_question_from_conversation,
    save_recall_question_to_spring,
)
from recall.recall_api_client import find_recall_pairs, get_recall_questions, send_recall_result
from recall.recall_score_calculator import calculate_final_recall_score

logger = logging.getLogger(__name__)

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")
RECORD_FETCH_RETRIES = 3
RECORD_FETCH_DELAY_SEC = 0.5


def _fetch_session_records(session_id: int) -> list[dict]:
    last_error: Exception | None = None

    for attempt in range(1, RECORD_FETCH_RETRIES + 1):
        try:
            resp = requests.get(
                f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
                timeout=10,
            )
            resp.raise_for_status()
            records = resp.json()
            if not isinstance(records, list):
                raise ValueError(f"records 응답 형식 오류: {type(records)}")

            if any(r.get("transcriptText") for r in records) or attempt == RECORD_FETCH_RETRIES:
                return records

            logger.info(
                "transcript 없음, 재시도 %s/%s sessionId=%s",
                attempt,
                RECORD_FETCH_RETRIES,
                session_id,
            )
        except Exception as exc:
            last_error = exc
            logger.warning(
                "세션 records 조회 실패 attempt=%s sessionId=%s: %s",
                attempt,
                session_id,
                exc,
            )

        time.sleep(RECORD_FETCH_DELAY_SEC)

    if last_error:
        raise last_error
    return []


def _save_ai_reply(record_id: int, reply_text: str) -> None:
    resp = requests.post(
        f"{SPRING_BASE_URL}/api/voice/reply",
        json={"recordId": record_id, "replyText": reply_text},
        timeout=10,
    )
    if resp.status_code >= 400:
        logger.error(
            "AI reply 저장 실패 recordId=%s status=%s body=%s",
            record_id,
            resp.status_code,
            resp.text,
        )
    resp.raise_for_status()


def _link_recall_question(record_id: int, question_id: int) -> None:
    resp = requests.post(
        f"{SPRING_BASE_URL}/api/voice/recall-link",
        json={
            "recordId": record_id,
            "recallQuestionId": question_id,
            "answerRole": "INITIAL",
        },
        timeout=10,
    )
    if resp.status_code >= 400:
        logger.error(
            "회상 질문 연결 실패 recordId=%s questionId=%s status=%s body=%s",
            record_id,
            question_id,
            resp.status_code,
            resp.text,
        )
    resp.raise_for_status()


def _resolve_ai_label(final_score: float) -> tuple[str, float]:
    if final_score >= 80:
        label = "GOOD"
    elif final_score >= 50:
        label = "PARTIAL"
    else:
        label = "LOW"
    return label, round(final_score / 100, 2)


def _save_recall_analysis(user_id: int, records: list[dict]) -> None:
    pairs = find_recall_pairs(records)
    if not pairs:
        return

    questions = get_recall_questions(user_id, SPRING_BASE_URL)

    for question_id, initial_record, recall_record in pairs:
        question = questions.get(question_id, {})
        past_text = initial_record.get("transcriptText") or ""
        current_text = recall_record.get("transcriptText") or ""

        if not past_text or not current_text:
            logger.info(
                "회상 분석 스킵 — transcript 없음 questionId=%s past=%s current=%s",
                question_id,
                bool(past_text),
                bool(current_text),
            )
            continue

        scores = calculate_final_recall_score(
            past_text=past_text,
            current_text=current_text,
            keywords=question.get("keywords") or [],
            question_type=question.get("questionType") or "DEFAULT",
        )
        final_score = scores["finalRecallScore"]
        ai_label, ai_confidence = _resolve_ai_label(final_score)

        resp = send_recall_result(
            recall_question_id=question_id,
            past_record_id=initial_record["recordId"],
            current_record_id=recall_record["recordId"],
            similarity_score=scores["similarityScore"],
            keyword_score=scores["keywordScore"],
            final_recall_score=final_score,
            ai_label=ai_label,
            ai_confidence=ai_confidence,
            base_url=SPRING_BASE_URL,
        )
        if resp.status_code >= 400:
            logger.error(
                "회상 분석 저장 실패 questionId=%s status=%s body=%s",
                question_id,
                resp.status_code,
                resp.text,
            )
        resp.raise_for_status()
        logger.info("회상 분석 저장 완료 questionId=%s finalScore=%s", question_id, final_score)


def process_voice_reply(record_id: int, session_id: int, user_id: int) -> None:
    try:
        records = _fetch_session_records(session_id)
        transcripts = [
            r["transcriptText"]
            for r in records
            if r.get("transcriptText")
        ]

        logger.info(
            "process 시작 recordId=%s sessionId=%s transcript_count=%s",
            record_id,
            session_id,
            len(transcripts),
        )

        result = generate_recall_question_from_conversation(transcripts)
        reply_text = result.get("question") or "오늘 이야기 즐거웠어요!"

        _save_ai_reply(record_id, reply_text)
        logger.info("AI reply 저장 완료 recordId=%s", record_id)

        memory_point = result.get("memoryPoint", "")
        if reply_text and memory_point:
            saved = save_recall_question_to_spring(
                user_id=user_id,
                memory_point=memory_point,
                question_text=reply_text,
                base_url=SPRING_BASE_URL,
            )
            question_id = saved.get("questionId")
            logger.info("회상 질문 저장 완료 userId=%s questionId=%s", user_id, question_id)
            if question_id:
                _link_recall_question(record_id, question_id)

        records = _fetch_session_records(session_id)
        _save_recall_analysis(user_id, records)

    except Exception as e:
        logger.exception("처리 실패 recordId=%s sessionId=%s: %s", record_id, session_id, e)
        try:
            _save_ai_reply(record_id, "잠시 오류가 생겼어요. 다시 말씀해 주시겠어요?")
        except Exception:
            logger.exception("fallback reply 저장도 실패 recordId=%s", record_id)
