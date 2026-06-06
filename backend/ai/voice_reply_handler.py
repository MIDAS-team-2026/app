"""
음성 업로드 후 AI 답변 생성 및 회상 질문 저장 로직.
STT는 Spring STTService가 업로드 시점에 처리하므로,
이 모듈은 Spring API에서 transcriptText를 조회해 사용한다.
"""

import json
import logging
import os
import requests

from recall.conversation_recall_generator import (
    generate_recall_question_from_conversation, save_recall_question_to_spring,
)
from recall.recall_api_client import analyze_session_recall

logger = logging.getLogger(__name__)
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


def _fetch_session_records(session_id: int) -> list[dict]:
    try:
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.error("세션 레코드 실시간 동기화 조회 실패: sessionId=%s, 에러=%s", session_id, e)
        return []


def _save_ai_reply(record_id: int, reply_text: str):
    """생성된 AI 오디오 가이드 텍스트 답변을 Spring DB에 저장합니다."""
    payload = {"recordId": record_id, "replyText": reply_text}
    resp = requests.post(f"{SPRING_BASE_URL}/api/voice/reply", json=payload, timeout=10)
    resp.raise_for_status()


def _link_recall_question(record_id: int, recall_question_id: int, answer_role: str):
    try:
        payload = {
            "recordId": record_id,
            "recallQuestionId": recall_question_id,
            "answerRole": answer_role
        }
        # Spring 인프라 구조의 세부 매핑 엔드포인트명에 맞추어 호출하세요.
        resp = requests.post(f"{SPRING_BASE_URL}/api/voice/record/link-recall", json=payload, timeout=10)
        resp.raise_for_status()
        logger.info("-> 성공적으로 레코드에 질문 ID 주입 완료: recordId=%d, questionId=%d, role=%s", record_id, recall_question_id, answer_role)
    except Exception as e:
        logger.error("-> 레코드 질문 ID 주입(Link) API 통신 실패: recordId=%d, 에러=%s", record_id, e)


def process_voice_reply(record_id: int, session_id: int, user_id: int, transcript_text: str):
    logger.info("AI 답변 및 매핑 시작: recordId=%s sessionId=%s", record_id, session_id)
    try:
        session_records = _fetch_session_records(session_id)
        if not session_records:
            session_records = [{"recordId": record_id, "transcriptText": transcript_text}]

        # 🔥 세션 ID를 파라미터로 넘겨, 정확한 턴을 계산하도록 변경
        reply_data = generate_recall_question_from_conversation(
            user_id=user_id,
            session_id=session_id,
            records=session_records
        )

        reply_text = reply_data.get("question")
        recall_question_id = reply_data.get("recallQuestionId")
        new_memory_point = reply_data.get("newMemoryPoint")
        new_question_text = reply_data.get("newQuestionText")

        # 1. AI 답변 전송
        if reply_text:
            _save_ai_reply(record_id=record_id, reply_text=reply_text)

        # 2. 🔥 [핵심] 이제 recall_question_id는 '노인이 정답을 말한 턴'에만 반환됩니다!
        # 따라서 현재 들어온 record_id(정답 문장)에 완벽하게 도장을 찍습니다.
        if recall_question_id:
            logger.info("정답 턴 감지. 현재 레코드를 RECALL로 매핑합니다. ID=%s", recall_question_id)
            _link_recall_question(record_id, recall_question_id, "RECALL")

        # 3. 새로운 기억 정보(INITIAL) 추출 시 DB에 저장 및 링크
        if new_memory_point and new_question_text:
            try:
                new_q = save_recall_question_to_spring(user_id, new_memory_point, new_question_text)
                new_q_id = new_q.get("questionId") if new_q else None
                if new_q_id:
                    _link_recall_question(record_id, new_q_id, "INITIAL")
            except Exception as e:
                logger.error("신규 질문 저장 실패: %s", e)

        # 4. 실시간 평가
        updated_records = _fetch_session_records(session_id)
        if not updated_records: updated_records = session_records
        analyze_session_recall(user_id=user_id, records=updated_records, speech_risk_score=0.0)

    except Exception as e:
        logger.exception("process_voice_reply 파이프라인 처리 중 치명적 예외 발생: %s", str(e))