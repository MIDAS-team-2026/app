from __future__ import annotations

import os
import tempfile
from functools import lru_cache
from pathlib import Path
from typing import Optional

import requests
import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, HttpUrl
from transformers import pipeline

from recall_score_prototype import (
    calculate_keyword_score,
    calculate_similarity_score,
    get_recall_weights,
)


DEFAULT_STT_MODEL = "openai/whisper-small"

app = FastAPI(title="MIDAS Recall/STT API")


class SttRequest(BaseModel):
    audioUrl: HttpUrl
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


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/stt", response_model=SttResponse)
def transcribe(request: SttRequest):
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
    similarity_score = calculate_similarity_score(request.pastText, request.currentText)
    keyword_score = calculate_keyword_score(request.keywords, request.currentText)
    similarity_weight, keyword_weight = get_recall_weights(request.questionType)

    final_score = similarity_score * similarity_weight + keyword_score * keyword_weight
    if request.questionType == "FACT" and keyword_score == 100:
        final_score = max(final_score, 90)
    if request.questionType == "FACT" and keyword_score == 0:
        final_score = min(final_score, 49)

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
        similarityScore=round(similarity_score, 2),
        keywordScore=round(keyword_score, 2),
        finalRecallScore=round(final_score, 2),
        aiLabel=label,
        aiConfidence=round(max(similarity_score, keyword_score) / 100, 2),
    )
