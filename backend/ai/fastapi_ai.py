"""
MIDAS AI 통합 FastAPI 서버. (Controller 역할)
"""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from typing import Optional

from fastapi import FastAPI, BackgroundTasks
from pydantic import BaseModel

from voice_reply_handler import (
    process_voice_reply,
    FIXED_QUESTIONS,
    _get_fixed_question_status,
    _pick_daily_fixed_question_index,
)
from main import run_record_mode, run_full_dummy_mode
from user_turn_analysis import analyze_user_turn
from session_speech_summary import summarize_session_speech
from recall.recall_api_client import analyze_session_recall, get_session_records
from recall.recall_score_calculator import calculate_final_recall_score

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")

app = FastAPI(title="MIDAS AI Server")

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

def run_session_batch_analysis(
    session_id: int,
    user_id: int,
    current_record_id: int | None = None,
) -> None:
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
        target_recall_record_id=current_record_id,
        send_all_recall_results=False,
    )

    logger.info(
        "batch-analysis 완료 sessionId=%s speechRiskScore=%s",
        session_id,
        speech_risk_score,
    )


def run_upload_analysis(
    record_id: int,
    session_id: int,
    user_id: int,
    audio_path: str | None,
    transcript_text: str,
    duration_sec: float,
) -> None:
    """
    음성 업로드 직후 실행되는 분석 흐름.
    AI 답변/회상 라벨링을 먼저 끝내고, 음성 분석 후 실제 speechRiskScore로
    세션 summary를 갱신한다.
    """
    logger.info("upload-analysis 시작 recordId=%s sessionId=%s", record_id, session_id)

    process_voice_reply(
        record_id,
        session_id,
        user_id,
        transcript_text,
        run_realtime_analysis=False,
    )

    mock_args = MainArgsMock(
        record_id=record_id,
        session_id=session_id,
        audio_path=audio_path,
        audio_url=audio_path,
        transcript_text=transcript_text,
        duration_sec=duration_sec,
    )
    run_record_mode(mock_args)

    run_session_batch_analysis(session_id, user_id, current_record_id=record_id)
    logger.info("upload-analysis 완료 recordId=%s sessionId=%s", record_id, session_id)

# ==========================
# API Endpoints
# ==========================
@app.get("/health")
def health():
    return {"status": "ok"}

@app.get("/opening-question")
def opening_question(userId: int | None = None):
    """
    세션 시작 인사말 뒤에 이어질 고정 질문 텍스트. Spring이 세션 시작 시 조회한다.
    - 온보딩(최초 5개) 전: 첫 고정 질문
    - 온보딩 후, 오늘 아직 안 물어봤으면: userId+오늘 날짜로 고정된 무작위 질문 1개
      (process_voice_reply가 실제 답변을 채점할 때도 같은 방식으로 골라서 서로 어긋나지 않는다)
    - 오늘 이미 물어봤으면: 없음
    """
    if userId is None:
        return {"questionText": FIXED_QUESTIONS[0]["questionText"]}

    status = _get_fixed_question_status(userId)

    if status["doneToday"]:
        return {"questionText": None}

    if not status["onboardingDone"]:
        return {"questionText": FIXED_QUESTIONS[0]["questionText"]}

    daily_index = _pick_daily_fixed_question_index(userId)
    return {"questionText": FIXED_QUESTIONS[daily_index]["questionText"]}

@app.post("/process")
def process(req: ProcessRequest, background_tasks: BackgroundTasks):
    logger.info(
        "process 수신 recordId=%s sessionId=%s",
        req.recordId,
        req.sessionId
    )

    background_tasks.add_task(
        run_upload_analysis,
        req.recordId,
        req.sessionId,
        req.userId,
        req.audioPath,
        req.transcriptText or "",
        req.durationSec or 0.0,
    )

    logger.info(
        "AI 응답 + 분석 + 세션 점수 갱신 작업 등록 완료 recordId=%s",
        req.recordId
    )

    return {"status": "processing"}

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
    logger.info(
        "배치 분석 트리거 수신: sessionId=%s, userId=%s",
        req.sessionId,
        req.userId,
    )
    background_tasks.add_task(run_session_batch_analysis, req.sessionId, req.userId)
    return {"status": "processing", "message": "Batch analysis started"}
