import requests

from recall_score_calculator import (
    calculate_final_recall_score,
    calculate_final_risk_score,
)

BASE_URL = "http://localhost:8080"


def send_recall_result(
    recall_question_id,
    past_record_id,
    current_record_id,
    similarity_score,
    keyword_score,
    final_recall_score,
):
    body = {
        "recallQuestionId": recall_question_id,
        "pastRecordId": past_record_id,
        "currentRecordId": current_record_id,
        "similarityScore": similarity_score,
        "keywordScore": keyword_score,
        "finalRecallScore": final_recall_score,
    }

    response = requests.post(
        f"{BASE_URL}/api/ai/analysis/recall",
        json=body,
        timeout=10,
    )

    return response


def send_risk_result(
    session_id,
    speech_score,
    text_score,
    recall_score,
    final_risk_score,
    risk_level,
):
    body = {
        "sessionId": session_id,
        "speechScore": speech_score,
        "textScore": text_score,
        "recallScore": recall_score,
        "finalRiskScore": final_risk_score,
        "riskLevel": risk_level,
    }

    response = requests.post(
        f"{BASE_URL}/api/ai/analysis/risk",
        json=body,
        timeout=10,
    )

    return response


if __name__ == "__main__":
    past_text = "대구에서 태어났어요"
    current_text = "대구요"
    keywords = ["대구"]
    question_type = "FACT"

    recall_scores = calculate_final_recall_score(
        past_text=past_text,
        current_text=current_text,
        keywords=keywords,
        question_type=question_type,
    )

    recall_response = send_recall_result(
        recall_question_id=1,
        past_record_id=1,
        current_record_id=2,
        similarity_score=recall_scores["similarityScore"],
        keyword_score=recall_scores["keywordScore"],
        final_recall_score=recall_scores["finalRecallScore"],
    )

    print("recall status:", recall_response.status_code)
    print(recall_response.text)

    risk_scores = calculate_final_risk_score(
        speech_score=90.0,
        text_score=90.0,
        recall_score=recall_scores["finalRecallScore"],
    )

    risk_response = send_risk_result(
        session_id=1,
        speech_score=90.0,
        text_score=90.0,
        recall_score=recall_scores["finalRecallScore"],
        final_risk_score=risk_scores["finalRiskScore"],
        risk_level=risk_scores["riskLevel"],
    )

    print("risk status:", risk_response.status_code)
    print(risk_response.text)