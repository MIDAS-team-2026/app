"""
MIDAS AI FastAPI 서버.

엔드포인트:
  POST /process        — 음성 업로드 완료 후 AI 답변 생성 (voice_reply_handler)
  POST /api/stt        — 음성 파일 STT 변환
  POST /api/recall/analyze — 회상 분석
  GET  /health         — 헬스체크
"""

import os
import tempfile
import threading
from functools import lru_cache
from pathlib import Path
from typing import Optional

import requests
import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, HttpUrl
from transformers import pipeline

from recall.conversation_recall_generator import (
    generate_recall_question_from_conversation,
    save_recall_question_to_spring,
)
from recall.recall_score_calculator import (
    calculate_final_recall_score,
)

app = FastAPI(title="MIDAS AI Server")

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")
DEFAULT_STT_MODEL = "openai/whisper-small"


# ──────────────────────────────────────────
# Models
# ──────────────────────────────────────────

class ProcessRequest(BaseModel):
    recordId: int
    sessionId: int
    userId: int


class SttRequest(BaseModel):
    audioUrl: str
    language: str = "ko"


class SttResponse(BaseModel):
    transcriptText: str
    confidence: Optional[float] = None
    modelName: str


class RecallAnalysisRequest(BaseModel):
    recallQuestionId: int
    pastRecordId: Optional[int] = None
    currentRecordId: int
    pastText: str
    currentText: str
    questionType: str = "FACT"
    keywords: list[str] = []


class RecallAnalysisResponse(BaseModel):
    recallQuestionId: int
    pastRecordId: Optional[int]
    currentRecordId: int
    similarityScore: float
    keywordScore: float
    finalRecallScore: float
    aiLabel: str
    aiConfidence: float


# ──────────────────────────────────────────
# STT 헬퍼
# ──────────────────────────────────────────

@lru_cache(maxsize=1)
def get_stt_pipeline():
    model_name = os.getenv("STT_MODEL_NAME", DEFAULT_STT_MODEL)
    device = 0 if torch.cuda.is_available() else -1
    return model_name, pipeline(
        task="automatic-speech-recognition",
        model=model_name,
        device=device,
    )


def download_audio(audio_url: str) -> Path:
    suffix = Path(audio_url.split("?")[0]).suffix or ".wav"
    response = requests.get(audio_url, timeout=60)
    response.raise_for_status()
    temp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    with temp:
        temp.write(response.content)
    return Path(temp.name)


# ──────────────────────────────────────────
# 엔드포인트
# ──────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/process")
def process(req: ProcessRequest):
    """Spring이 음성 업로드 완료 후 호출. 즉시 200 반환 후 백그라운드 처리."""
    threading.Thread(
        target=_process,
        args=(req.recordId, req.sessionId, req.userId),
        daemon=True,
    ).start()
    return {"status": "processing"}


@app.post("/api/stt", response_model=SttResponse)
def transcribe(request: SttRequest):
    """음성 URL을 받아 STT 변환 후 텍스트 반환."""
    audio_path: Optional[Path] = None
    try:
        audio_path = download_audio(str(request.audioUrl))
        model_name, transcriber = get_stt_pipeline()
        result = transcriber(
            str(audio_path),
            generate_kwargs={"language": request.language, "task": "transcribe"},
            return_timestamps=False,
        )
        return SttResponse(
            transcriptText=str(result.get("text", "")).strip(),
            confidence=None,
            modelName=model_name,
        )
    except requests.RequestException as exc:
        raise HTTPException(status_code=400, detail=f"audio download failed: {exc}") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"stt failed: {exc}") from exc
    finally:
        if audio_path is not None:
            audio_path.unlink(missing_ok=True)


@app.post("/api/recall/analyze", response_model=RecallAnalysisResponse)
def analyze_recall(request: RecallAnalysisRequest):
    """회상 텍스트 유사도 및 키워드 점수 분석."""
    scores = calculate_final_recall_score(
        past_text=request.pastText,
        current_text=request.currentText,
        keywords=request.keywords,
        question_type=request.questionType,
    )

    final_score = scores["finalRecallScore"]
    if final_score >= 80:
        label = "GOOD"
    elif final_score >= 50:
        label = "PARTIAL"
    else:
        label = "LOW"

    return RecallAnalysisResponse(
        recallQuestionId=request.recallQuestionId,
        pastRecordId=request.pastRecordId,
        currentRecordId=request.currentRecordId,
        similarityScore=round(scores.get("similarityScore", 0), 2),
        keywordScore=round(scores.get("keywordScore", 0), 2),
        finalRecallScore=round(final_score, 2),
        aiLabel=label,
        aiConfidence=round(max(scores.get("similarityScore", 0), scores.get("keywordScore", 0)) / 100, 2),
    )


# ──────────────────────────────────────────
# 내부 처리 함수
# ──────────────────────────────────────────

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
