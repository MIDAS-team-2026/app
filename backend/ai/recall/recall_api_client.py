import argparse
import logging
from typing import Dict, List
import requests

from .recall_score_calculator import (
    calculate_final_recall_score,
    calculate_final_risk_score,
)

BASE_URL = "http://localhost:8080"
logger = logging.getLogger(__name__)


def get_session_records(session_id: int, base_url: str = BASE_URL) -> List[dict]:
    response = requests.get(
        f"{base_url}/api/voice/session/{session_id}/records",
        timeout=10,
    )
    response.raise_for_status()
    return response.json()


def get_recall_questions(user_id: int, base_url: str = BASE_URL) -> Dict[int, dict]:
    response = requests.get(
        f"{base_url}/api/recall/questions/{user_id}",
        timeout=10,
    )
    response.raise_for_status()

    questions = response.json()
    return {
        int(question["questionId"]): question
        for question in questions
    }


def send_recall_result(
        recall_question_id: int,
        past_record_id: int,
        current_record_id: int,
        similarity_score: float,
        keyword_score: float,
        final_recall_score: float,
        base_url: str = BASE_URL,
):
    # 만약 과거 세션 질문이라 past_record_id가 없는 경우 0 또는 None 허용 처리
    payload = {
        "recallQuestionId": recall_question_id,
        "pastRecordId": past_record_id if past_record_id else 0,
        "currentRecordId": current_record_id,
        "similarityScore": similarity_score,
        "keywordScore": keyword_score,
        "finalRecallScore": final_recall_score,
    }
    response = requests.post(
        f"{base_url}/api/recall/results",
        json=payload,
        timeout=10,
    )
    return response


def send_risk_result(
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