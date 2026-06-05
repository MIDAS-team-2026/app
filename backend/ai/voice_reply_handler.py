"""
업로드된 음성 레코드에 대한 AI 답변을 생성하고 Spring에 저장하는 FastAPI 서버.

Spring이 음성 업로드 완료 후 POST /process 를 호출하면:
  1. 해당 세션의 대화 기록(transcript)을 Spring에서 가져옴
  2. conversation_recall_generator로 회상 질문 생성 → AI 답변으로 사용
  3. POST /api/voice/reply 로 Spring에 저장
  4. POST /api/recall/questions 로 회상 질문도 저장
"""

import os
import threading

import requests
from fastapi import FastAPI
from pydantic import BaseModel

from recall.conversation_recall_generator import (
    generate_recall_question_from_conversation,
    save_recall_question_to_spring,
)

app = FastAPI()

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


class ProcessRequest(BaseModel):
    recordId: int
    sessionId: int
    userId: int


def _process(record_id: int, session_id: int, user_id: int) -> None:
    """백그라운드에서 실행 — Spring이 응답을 기다리지 않아도 된다."""
    try:
        # 1. 세션 대화 기록 가져오기
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
            timeout=10,
        )
        resp.raise_for_status()
        records = resp.json()

        # transcript 텍스트만 추출 (비어 있는 것 제외)
        transcripts = [
            r["transcriptText"]
            for r in records
            if r.get("transcriptText")
        ]

        # 2. 대화 내용으로 회상 질문(= AI 답변) 생성
        result = generate_recall_question_from_conversation(transcripts)
        reply_text = result.get("question") or "오늘 이야기 즐거웠어요!"

        # 3. AI 답변을 Spring에 저장 → 앱 폴링에서 수신
        requests.post(
            f"{SPRING_BASE_URL}/api/voice/reply",
            json={"recordId": record_id, "replyText": reply_text},
            timeout=10,
        ).raise_for_status()

        # 4. 회상 질문도 별도 저장 (기존 흐름 유지)
        memory_point = result.get("memoryPoint", "")
        if reply_text and memory_point:
            save_recall_question_to_spring(
                user_id=user_id,
                memory_point=memory_point,
                question_text=reply_text,
                base_url=SPRING_BASE_URL,
            )

    except Exception as e:
        print(f"[voice_reply_handler] 처리 실패 record_id={record_id}: {e}")

        # 실패 시에도 앱 폴링이 무한 대기하지 않도록 에러 메시지 저장
        try:
            requests.post(
                f"{SPRING_BASE_URL}/api/voice/reply",
                json={
                    "recordId": record_id,
                    "replyText": "잠시 오류가 생겼어요. 다시 말씀해 주시겠어요?",
                },
                timeout=5,
            )
        except Exception:
            pass


@app.post("/process")
def process(req: ProcessRequest):
    """
    Spring이 음성 업로드 완료 후 호출하는 엔드포인트.
    즉시 200 반환 후 백그라운드에서 AI 답변 생성.
    """
    threading.Thread(
        target=_process,
        args=(req.recordId, req.sessionId, req.userId),
        daemon=True,
    ).start()
    return {"status": "processing"}
