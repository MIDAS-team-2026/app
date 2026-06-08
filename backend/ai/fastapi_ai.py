"""
MIDAS AI 통합 FastAPI 서버. (Controller 역할)
"""
from __future__ import annotations

import logging
import os
import tempfile
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Optional
from groq import Groq

import requests
import torch
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
from transformers import pipeline

from voice_reply_handler import process_voice_reply
from main import run_record_mode, run_full_dummy_mode
from user_turn_analysis import analyze_user_turn
from session_speech_summary import summarize_session_speech
from recall.recall_api_client import analyze_session_recall, get_session_records
from recall.recall_score_calculator import calculate_final_recall_score
from recall.recall_api_client import analyze_session_recall, get_session_records

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

DEFAULT_STT_MODEL = "openai/whisper-small"
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")

app = FastAPI(title="MIDAS AI Server")
groq_client = Groq()

# ==========================
# Pydantic Schemas
# ==========================
class ProcessRequest(BaseModel):
    recordId: int
    sessionId: int
    userId: int

    audioPath: str | None = None
    transcriptText: str | None = None
    durationSec: float | None = 0.0

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

class RecordAnalysisRequest(BaseModel):
    recordId: int
    sessionId: int
    audioPath: str = ""
    audioUrl: str = ""
    transcriptText: str = ""
    durationSec: float = 0.0

class BatchAnalysisRequest(BaseModel):
    sessionId: int
    userId: int

@dataclass
class MainArgsMock:
    record_id: int
    session_id: int
    audio_path: str
    audio_url: str
    transcript_text: str
    duration_sec: float
    mode: str = "record"

# ==========================
# STT Utility
# ==========================
@lru_cache(maxsize=1)
def get_stt_pipeline():
    model_name = os.getenv("STT_MODEL_NAME", DEFAULT_STT_MODEL)
    device = 0 if torch.cuda.is_available() else -1
    return model_name, pipeline(task="automatic-speech-recognition", model=model_name, device=device)

def download_audio(audio_url: str) -> Path:
    suffix = Path(audio_url.split("?")[0]).suffix or ".wav"
    response = requests.get(audio_url, timeout=60)
    response.raise_for_status()
    temp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    with temp:
        temp.write(response.content)
    return Path(temp.name)

def run_session_batch_analysis(session_id: int, user_id: int) -> None:
    """
    세션 종료 후 전체 record를 기준으로 회상 분석과 최종 위험도 저장을 실행한다.
    """
    logger.info("batch-analysis 시작 sessionId=%s userId=%s", session_id, user_id)

    records = get_session_records(session_id, SPRING_BASE_URL)
    turn_results = []

    for record in records:
        transcript_text = record.get("transcriptText") or ""
        audio_path = record.get("audioFilePath") or ""

        if not transcript_text and not audio_path:
            logger.info("batch record skip recordId=%s transcript/audio 없음", record.get("recordId"))
            continue

        turn_result = analyze_user_turn(
            record_id=int(record["recordId"]),
            session_id=session_id,
            audio_path=audio_path,
            audio_url=audio_path,
            transcript_text=transcript_text,
            duration_sec=0.0,
        )
        turn_results.append(turn_result)

    speech_summary = summarize_session_speech(
        session_id=session_id,
        turn_results=turn_results,
    )
    speech_risk_score = speech_summary.get("speechRiskScore", 0.0)

    analyze_session_recall(
        user_id=user_id,
        session_id=session_id,
        speech_risk_score=speech_risk_score,
        base_url=SPRING_BASE_URL,
    )

    logger.info(
        "batch-analysis 완료 sessionId=%s speechRiskScore=%s",
        session_id,
        speech_risk_score,
    )

# ==========================
# API Endpoints
# ==========================
@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/process")
def process(req: ProcessRequest, background_tasks: BackgroundTasks):
    logger.info(
        "process 수신 recordId=%s sessionId=%s",
        req.recordId,
        req.sessionId
    )

    # 1. AI 답변 생성
    background_tasks.add_task(
        process_voice_reply,
        req.recordId,
        req.sessionId,
        req.userId,
        req.transcriptText or ""
    )

    # 2. 음성/텍스트 분석
    mock_args = MainArgsMock(
        record_id=req.recordId,
        session_id=req.sessionId,
        audio_path=req.audioPath,
        audio_url=req.audioPath,
        transcript_text=req.transcriptText or "",
        duration_sec=req.durationSec or 0.0
    )

    background_tasks.add_task(
        run_record_mode,
        mock_args
    )

    logger.info(
        "AI 응답 + 분석 작업 등록 완료 recordId=%s",
        req.recordId
    )

    return {"status": "processing"}

@app.post("/api/stt", response_model=SttResponse)
def transcribe(request: SttRequest):
    audio_path = None
    try:
        # 1. URL로부터 오디오 파일 다운로드
        audio_path = download_audio(request.audioUrl)

        # 2. Groq Whisper API로 STT 수행
        with open(audio_path, "rb") as audio_file:
            transcription = groq_client.audio.transcriptions.create(
                file=(os.path.basename(audio_path), audio_file.read()),
                model="whisper-large-v3",
                language=request.language if request.language else "ko",
                response_format="json"
            )

        raw_text = transcription.text.strip()

        # 3. 만약 텍스트가 비어있다면 정제 패스
        if not raw_text:
            return SttResponse(transcriptText="", confidence=None, modelName="groq-whisper-large-v3")

        # 4. Groq 무료 LLM(Llama 3)을 사용해 AI 텍스트 정제 수행
        # (간투사 제거, 오타 교정, 핵심 정보 보존)
        refine_prompt = f"""
        당신은 텍스트 정제 전문 AI입니다. 
        어르신의 음성을 STT로 변환한 날것의 문장이 주어집니다. 아래 규칙에 맞게 깔끔한 문장으로 교정해 주세요.

        [규칙]
        1. 의미 없는 말더듬이나 간투사('어...', '음...', '그...', '아니 그게')는 자연스럽게 삭제하세요.
        2. STT 오인식으로 보이는 명백한 맞춤법 오타나 조사 오류를 문맥에 맞게 수정하세요. (예: "오늘 월루일이지?" -> "오늘 월요일이지?")
        3. 어르신이 하신 말씀의 본래 의미나 핵심 데이터(날짜, 이름, 장소)는 절대로 왜곡하거나 생략하지 마세요.
        4. 오직 정제된 최종 결과 문장만 출력하세요. 다른 설명이나 따옴표는 절대 붙이지 마세요.

        입력된 날것의 문장: "{raw_text}"
        정제된 문장:
        """.strip()

        # llama-3.3-70b-versatile 또는 llama3-8b-8192 모델 사용
        llm_response = groq_client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=[{"role": "user", "content": refine_prompt}],
            temperature=0.1,  # 정확한 교정을 위해 창의성 최소화
        )

        clean_text = llm_response.choices[0].message.content.strip()

        # 5. 스프링이 원하는 포맷에 맞춰 정제된 텍스트 리턴
        return SttResponse(
            transcriptText=clean_text,
            confidence=None,
            modelName="groq-whisper-large-v3 + llama3"
        )

    except requests.RequestException as exc:
        logger.error(f"오디오 다운로드 실패: {exc}")
        raise HTTPException(status_code=400, detail=f"audio download failed: {exc}")

    except Exception as exc:
        logger.exception("STT 및 정제 연산 중 에러 발생: %s", exc)
        raise HTTPException(status_code=500, detail=f"stt processing failed: {str(exc)}")

    finally:
        # 임시 오디오 파일 삭제 정리
        if audio_path is not None and audio_path.exists():
            try:
                audio_path.unlink(missing_ok=True)
            except Exception as e:
                logger.warning(f"임시 파일 삭제 실패: {e}")

# @app.post("/api/analysis/record")
# def analyze_single_record(req: RecordAnalysisRequest):
#     mock_args = MainArgsMock(
#         record_id=req.recordId, session_id=req.sessionId,
#         audio_path=req.audioPath, audio_url=req.audioUrl,
#         transcript_text=req.transcriptText, duration_sec=req.durationSec
#     )
#     return run_record_mode(mock_args)

@app.post("/api/analysis/record")
def analyze_single_record(req: RecordAnalysisRequest):

    logger.info(
        "분석 시작 recordId=%s sessionId=%s",
        req.recordId,
        req.sessionId
    )

    mock_args = MainArgsMock(
        record_id=req.recordId,
        session_id=req.sessionId,
        audio_path=req.audioPath,
        audio_url=req.audioUrl,
        transcript_text=req.transcriptText,
        duration_sec=req.durationSec
    )

    result = run_record_mode(mock_args)

    logger.info(
        "분석 종료 recordId=%s sessionId=%s",
        req.recordId,
        req.sessionId
    )

    return result

@app.get("/api/analysis/dummy")
def run_dummy_analysis():
    return run_full_dummy_mode()

@app.post("/api/recall/analyze", response_model=RecallAnalysisResponse)
def analyze_recall(request: RecallAnalysisRequest):
    scores = calculate_final_recall_score(
        past_text=request.pastText, current_text=request.currentText,
        keywords=request.keywords, question_type=request.questionType,
    )
    final_score = scores["finalRecallScore"]
    label = "GOOD" if final_score >= 80 else ("PARTIAL" if final_score >= 50 else "LOW")

    return RecallAnalysisResponse(
        recallQuestionId=request.recallQuestionId, pastRecordId=request.pastRecordId,
        currentRecordId=request.currentRecordId, similarityScore=round(scores.get("similarityScore", 0), 2),
        keywordScore=round(scores.get("keywordScore", 0), 2), finalRecallScore=round(final_score, 2),
        aiLabel=label, aiConfidence=round(max(scores.get("similarityScore", 0), scores.get("keywordScore", 0)) / 100, 2)
    )

# Spring의 세션 종료 및 배치 분석 트리거 수신 엔드포인트
@app.post("/api/ai/batch-analysis")
def trigger_batch_analysis(req: BatchAnalysisRequest, background_tasks: BackgroundTasks):
    speech_score = getattr(req, "speech_risk_score", getattr(req, "speechRiskScore", 0.0))
    logger.info("배치 분석 트리거 수신: sessionId=%s, userId=%s, speechRiskScore=%s",
                req.sessionId, req.userId, speech_score)

    def background_analysis_job():
        try:
            # 1. sessionId를 이용해 현재 세션의 모든 대화 기록(records)을 먼저 가져옵니다.
            records = get_session_records(req.sessionId)

            # 2. 바뀐 함수 스펙에 맞춰 이름 지정(Keyword argument) 방식으로 정확하게 주입합니다.
            analyze_session_recall(
                user_id=req.userId,
                records=records,
                speech_risk_score=speech_score
            )
        except Exception as e:
            logger.error("백그라운드 배치 분석 중 치명적 에러 발생: %s", e)

    # 3. 래핑한 안전한 함수를 백그라운드 태스크로 넘깁니다.
    background_tasks.add_task(background_analysis_job)
    return {"status": "ok", "message": "Batch analysis task registered"}