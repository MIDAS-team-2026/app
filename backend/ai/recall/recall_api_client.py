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
        session_id: int,
        speech_risk_score: float,
        recall_score: float,
        final_risk_score: float,
        risk_level: str,
        base_url: str = BASE_URL,
):
    payload = {
        "sessionId": session_id,
        "speechRiskScore": speech_risk_score,
        "recallScore": recall_score,
        "finalRiskScore": final_risk_score,
        "riskLevel": risk_level,
    }
    response = requests.post(
        f"{base_url}/api/session/risk",
        json=payload,
        timeout=10,
    )
    return response


def analyze_session_recall(user_id: int, records: List[dict], speech_risk_score: float = 0.0, base_url: str = BASE_URL):
    logger.info("analyze_session_recall 시작: 총 records 수=%d", len(records))

    # 1. 사용자의 전체 과거 질문 리스트(정답 정보 포함)를 Spring DB에서 선제적 조회
    try:
        past_questions = get_recall_questions(user_id, base_url)
    except Exception as e:
        logger.error("분석을 위한 과거 질문 조회 실패: %s", e)
        return

    final_recall_scores = []

    # 2. 이번 세션의 레코드를 순회하며 RECALL(회상 답변) 턴을 찾음
    for r in records:
        answer_role = r.get("answerRole")
        question_id = r.get("recallQuestionId")
        current_record_id = r.get("recordId")
        current_text = r.get("transcriptText", "")

        if answer_role == "RECALL" and question_id is not None:
            question_id_int = int(question_id)
            logger.info("RECALL 레코드 발견: recordId=%s, questionId=%d", current_record_id, question_id_int)

            past_text = ""
            past_record_id = 0
            keywords = []

            # [핵심 로직 1] 우선 이번 세션 안에서 생성된 INITIAL이 있는지 찾아봄
            initial_record = None
            for rec in records:
                if rec.get("answerRole") == "INITIAL" and rec.get("recallQuestionId") == question_id_int:
                    initial_record = rec
                    break

            if initial_record:
                past_text = initial_record.get("transcriptText", "")
                past_record_id = initial_record.get("recordId")
                keywords = [w for w in past_text.split() if len(w) > 1]
                logger.info("-> 동일 세션 내 INITIAL과 매칭 성공")

            # [핵심 로직 2] 이번 세션에 없다면? -> 과거 DB의 정답(expectedAnswer)을 기준으로 채점!
            elif question_id_int in past_questions:
                q_info = past_questions[question_id_int]
                past_text = q_info.get("expectedAnswer", "")
                past_record_id = 0  # 과거 세션이므로 0으로 처리
                keywords = [w.strip() for w in past_text.split() if len(w.strip()) > 0]
                logger.info("-> 과거 질문 DB와 매칭 성공 (과거 정답: '%s')", past_text)

            else:
                logger.warning("-> [경고] ID=%d 에 해당하는 초기 정보가 세션/과거 DB 어디에도 없습니다.", question_id_int)
                continue

            # 3. 매칭된 텍스트(past_text)를 기준으로 회상 점수 계산
            try:
                scores = calculate_final_recall_score(
                    past_text=past_text,
                    current_text=current_text,
                    keywords=keywords,
                    question_type="RECALL"
                )

                # 4. 분석 결과 백엔드(Spring) 전송
                send_recall_result(
                    recall_question_id=question_id_int,
                    past_record_id=past_record_id,
                    current_record_id=current_record_id,
                    similarity_score=scores["similarityScore"],
                    keyword_score=scores["keywordScore"],
                    final_recall_score=scores["finalRecallScore"],
                    base_url=base_url
                )
                final_recall_scores.append(scores["finalRecallScore"])
            except Exception as e:
                logger.error("   점수 계산/전송 중 오류 발생: %s", e)

    # 5. 종합 위험도 계산 및 전송
    if final_recall_scores:
        recall_score = round(sum(final_recall_scores) / len(final_recall_scores), 2)
    else:
        logger.warning("정상적으로 분석된 회상 답변 쌍이 없습니다. 기본값 0.0 설정.")
        recall_score = 0.0

    risk_scores = calculate_final_risk_score(speech_risk_score, recall_score)

    try:
        session_id = records[0].get("sessionId") if records else 0
        send_risk_result(session_id, speech_risk_score, recall_score, risk_scores["finalRiskScore"], risk_scores["riskLevel"], base_url)
    except Exception as e:
        logger.error("종합 위험도 전송 실패: %s", e)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--session-id", type=int, required=True)
    parser.add_argument("--speech-risk-score", type=float, default=0.0)
    parser.add_argument("--base-url", default=BASE_URL)

    args = parser.parse_args()

    try:
        records = get_session_records(args.session_id, args.base_url)
        analyze_session_recall(
            user_id=args.user_id,
            records=records,
            speech_risk_score=args.speech_risk_score,
            base_url=args.base_url
        )
    except Exception as e:
        print("메인 실행 실패:", e)


if __name__ == "__main__":
    main()