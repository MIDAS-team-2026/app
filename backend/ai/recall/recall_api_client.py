import argparse
from typing import Dict, List

import requests

from recall.recall_score_calculator import (
    calculate_final_recall_score,
    calculate_final_risk_score,
)

BASE_URL = "http://localhost:8080"


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
    ai_label: str | None = None,
    ai_confidence: float | None = None,
    base_url: str = BASE_URL,
):
    body = {
        "recallQuestionId": recall_question_id,
        "pastRecordId": past_record_id,
        "currentRecordId": current_record_id,
        "similarityScore": similarity_score,
        "keywordScore": keyword_score,
        "finalRecallScore": final_recall_score,
    }
    if ai_label is not None:
        body["aiLabel"] = ai_label
    if ai_confidence is not None:
        body["aiConfidence"] = ai_confidence

    return requests.post(
        f"{base_url}/api/ai/analysis/recall",
        json=body,
        timeout=10,
    )


def send_risk_result(
    session_id: int,
    speech_risk_score: float,
    recall_score: float,
    final_risk_score: float,
    risk_level: str,
    base_url: str = BASE_URL,
):
    body = {
        "sessionId": session_id,
        "speechScore": speech_risk_score,
        "textScore": 0.0,
        "recallScore": recall_score,
        "finalRiskScore": final_risk_score,
        "riskLevel": risk_level,
    }

    return requests.post(
        f"{base_url}/api/ai/analysis/risk",
        json=body,
        timeout=10,
    )


def find_recall_pairs(records: List[dict]) -> List[tuple]:
    initial_by_question: Dict[int, dict] = {}
    recall_by_question: Dict[int, dict] = {}

    for record in records:
        question_id = record.get("recallQuestionId")
        answer_role = record.get("answerRole")

        if question_id is None or answer_role is None:
            continue

        question_id = int(question_id)

        if answer_role == "INITIAL":
            initial_by_question[question_id] = record
        elif answer_role == "RECALL":
            recall_by_question[question_id] = record

    pairs = []

    for question_id, recall_record in recall_by_question.items():
        initial_record = initial_by_question.get(question_id)

        if initial_record is None:
            parent_id = recall_record.get("parentRecordId")
            if parent_id is not None:
                initial_record = next(
                    (r for r in records if r.get("recordId") == parent_id),
                    None,
                )

        if initial_record is None:
            continue

        pairs.append((question_id, initial_record, recall_record))

    return pairs


def analyze_session_recall(
    user_id: int,
    session_id: int,
    speech_risk_score: float,
    base_url: str = BASE_URL,
) -> None:
    records = get_session_records(session_id, base_url)
    questions = get_recall_questions(user_id, base_url)

    pairs = find_recall_pairs(records)

    if not pairs:
        print("분석 가능한 INITIAL/RECALL 답변 쌍이 없습니다.")
        print("INITIAL과 RECALL은 같은 recallQuestionId를 사용해야 합니다.")
        return

    final_recall_scores = []

    for question_id, initial_record, recall_record in pairs:
        question = questions.get(question_id, {})

        past_text = initial_record.get("transcriptText") or ""
        current_text = recall_record.get("transcriptText") or ""

        keywords = question.get("keywords") or []
        question_type = question.get("questionType") or "DEFAULT"

        recall_scores = calculate_final_recall_score(
            past_text=past_text,
            current_text=current_text,
            keywords=keywords,
            question_type=question_type,
        )

        recall_response = send_recall_result(
            recall_question_id=question_id,
            past_record_id=initial_record["recordId"],
            current_record_id=recall_record["recordId"],
            similarity_score=recall_scores["similarityScore"],
            keyword_score=recall_scores["keywordScore"],
            final_recall_score=recall_scores["finalRecallScore"],
            base_url=base_url,
        )

        print(
            f"recall question {question_id} status:",
            recall_response.status_code,
            recall_response.text,
        )
        recall_response.raise_for_status()

        final_recall_scores.append(recall_scores["finalRecallScore"])

    recall_score = round(sum(final_recall_scores) / len(final_recall_scores), 2)

    risk_scores = calculate_final_risk_score(
        speech_risk_score=speech_risk_score,
        recall_score=recall_score,
    )

    risk_response = send_risk_result(
        session_id=session_id,
        speech_risk_score=speech_risk_score,
        recall_score=recall_score,
        final_risk_score=risk_scores["finalRiskScore"],
        risk_level=risk_scores["riskLevel"],
        base_url=base_url,
    )

    print("risk status:", risk_response.status_code, risk_response.text)
    risk_response.raise_for_status()

    print("분석 완료")
    print("recallScore:", recall_score)
    print("finalRiskScore:", risk_scores["finalRiskScore"])
    print("riskLevel:", risk_scores["riskLevel"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--session-id", type=int, required=True)
    parser.add_argument("--speech-risk-score", type=float, default=0.0)
    parser.add_argument("--base-url", default=BASE_URL)

    args = parser.parse_args()

    analyze_session_recall(
        user_id=args.user_id,
        session_id=args.session_id,
        speech_risk_score=args.speech_risk_score,
        base_url=args.base_url,
    )


if __name__ == "__main__":
    main()