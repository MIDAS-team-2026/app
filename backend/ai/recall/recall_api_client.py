import argparse
import re
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Dict, List, Optional

import requests
from recall.episode_context import ordered_records

from recall.recall_score_calculator import (
    COGNITIVE_RISK_WEIGHT,
    SPEECH_RISK_WEIGHT,
    calculate_final_recall_score,
    clamp_score, is_valid_score,
)

# text_linguistic_markers는 analysis/src에 있다. main.py를 거쳐 import될 때는
# 이미 sys.path에 잡혀 있지만, import 순서에 기대지 않도록 여기서도 직접 추가한다.
_ANALYSIS_SRC = Path(__file__).resolve().parents[1] / "analysis" / "src"

if str(_ANALYSIS_SRC) not in sys.path:
    sys.path.append(str(_ANALYSIS_SRC))

import text_linguistic_markers

BASE_URL = "http://localhost:8080"


PLACEHOLDER_EXPECTED_ANSWERS = {
    "USER_NAME",
    "SPOUSE_NAME",
    "HOMETOWN",
    "BIRTH_DATE",
    "FAMILY_NAME",
    "TODAY_WEEKDAY",
    "TODAY_DATE",
}

KOREAN_WEEKDAYS = [
    "월요일",
    "화요일",
    "수요일",
    "목요일",
    "금요일",
    "토요일",
    "일요일",
]
KOREAN_TIMEZONE = timezone(timedelta(hours=9))


def clean_text(text: str) -> str:
    text = str(text or "").strip()
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", "", text)
    text = re.sub(r"\s+", " ", text)
    return text


def get_session_records(session_id: int, base_url: str = BASE_URL) -> List[dict]:
    response = requests.get(
        f"{base_url}/api/voice/session/{session_id}/records",
        timeout=10,
    )
    response.raise_for_status()
    return response.json()


def get_linguistic_marker_history(user_id: int, base_url: str = BASE_URL) -> List[dict]:
    """개인 기준선 계산용으로 이 사용자의 과거 세션 언어 지표들을 가져온다.

    /api/ai/analysis/linguistic-markers/user/{userId}는 ApiResponse로 감싸서
    내려오므로 "data" 필드를 꺼내 써야 한다(다른 일부 GET 엔드포인트와 다름).
    """
    response = requests.get(
        f"{base_url}/api/ai/analysis/linguistic-markers/user/{user_id}",
        timeout=10,
    )
    response.raise_for_status()

    return response.json().get("data") or []


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
    evaluation_status: str | None = None,
    evaluation_reason: str | None = None,
):
    body = {
        "recallQuestionId": recall_question_id,
        "pastRecordId": past_record_id,
        "currentRecordId": current_record_id,
        "similarityScore": similarity_score,
        "keywordScore": keyword_score,
        "finalRecallScore": final_recall_score,
        "evaluationStatus": evaluation_status,
        "evaluationReason": evaluation_reason,
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
    text_score: float,
    recall_score: float,
    final_risk_score: float,
    risk_level: str,
    base_url: str = BASE_URL,
    evaluation_status: str | None = None,
    evaluation_reason: str | None = None,
    evaluated_recall_count: int | None = None,
    total_recall_count: int | None = None,
):
    body = {
        "sessionId": session_id,
        "speechScore": speech_risk_score,
        "textScore": text_score,
        "recallScore": recall_score,
        "finalRiskScore": final_risk_score,
        "riskLevel": risk_level,
        "evaluationStatus": evaluation_status,
        "evaluationReason": evaluation_reason,
        "evaluatedRecallCount": evaluated_recall_count,
        "totalRecallCount": total_recall_count,
    }

    return requests.post(
        f"{base_url}/api/ai/analysis/risk",
        json=body,
        timeout=10,
    )


def send_linguistic_markers(
    session_id: int,
    markers: dict,
    deviations: dict,
    baseline_sample_size: int,
    base_url: str = BASE_URL,
):
    body = {
        "sessionId": session_id,
        "pronounNounRatio": markers["pronounNounRatio"],
        "nounRatio": markers["nounRatio"],
        "lexicalDiversityMattr": markers["lexicalDiversityMattr"],
        "repetitionScore": markers["repetitionScore"],
        "pronounNounRatioZScore": deviations["pronounNounRatio"]["zScore"],
        "nounRatioZScore": deviations["nounRatio"]["zScore"],
        "lexicalDiversityMattrZScore": deviations["lexicalDiversityMattr"]["zScore"],
        "repetitionScoreZScore": deviations["repetitionScore"]["zScore"],
        "baselineSampleSize": baseline_sample_size,
    }

    return requests.post(
        f"{base_url}/api/ai/analysis/linguistic-markers",
        json=body,
        timeout=10,
    )


def compute_and_send_linguistic_markers(
    user_id: int,
    session_id: int,
    records: List[dict],
    base_url: str = BASE_URL,
) -> Optional[dict]:
    """세션의 모든 사용자 발화로 언어 지표(대명사:명사 비율 등)를 계산해서 저장한다.

    회상 정답 여부와 무관하게 세션 전체의 "말하는 방식"을 보는 지표라,
    고정질문/회상질문 답변을 가리지 않고 세션의 모든 발화를 사용한다.

    전체 사용자 기준 "정상 범위"는 아직 근거가 없어 사용하지 않고, 이 사용자
    본인의 과거 세션 평균 대비 오늘이 얼마나 벗어났는지(z-score)를 함께 계산한다.
    """
    texts = [
        str(record.get("transcriptText") or "").strip()
        for record in records
    ]
    texts = [text for text in texts if text]

    if not texts:
        return None

    markers = {
        "pronounNounRatio": text_linguistic_markers.pronoun_noun_ratio(texts),
        "nounRatio": text_linguistic_markers.noun_ratio(texts),
        "lexicalDiversityMattr": text_linguistic_markers.lexical_diversity_mattr(texts),
        "repetitionScore": text_linguistic_markers.repetition_score(texts),
    }

    history = get_linguistic_marker_history(user_id=user_id, base_url=base_url)
    baseline_sample_size = len(history)

    deviations = {
        metric_key: text_linguistic_markers.compute_baseline_deviation(
            history_values=[item.get(metric_key) for item in history],
            current_value=markers[metric_key],
        )
        for metric_key in markers
    }

    response = send_linguistic_markers(
        session_id=session_id,
        markers=markers,
        deviations=deviations,
        baseline_sample_size=baseline_sample_size,
        base_url=base_url,
    )

    print("linguistic markers status:", response.status_code, response.text)
    response.raise_for_status()

    return {"markers": markers, "deviations": deviations}


def find_recall_pairs(records: List[dict]) -> List[tuple]:
    try:
        ordered = ordered_records(records)
    except (TypeError, ValueError, OverflowError):
        return []
    preceding = {row.get("recordId"): ordered[index - 1]
                 for index, row in enumerate(ordered) if index}
    initial_by_question: Dict[int, List[dict]] = {}
    recall_by_question: Dict[int, List[dict]] = {}

    for record in records:
        question_id = record.get("recallQuestionId")
        answer_role = str(record.get("answerRole") or "").upper()

        if question_id is None or not answer_role:
            continue

        question_id = int(question_id)

        if answer_role == "INITIAL":
            initial_by_question.setdefault(question_id, []).append(record)
        elif answer_role == "RECALL":
            recall_by_question.setdefault(question_id, []).append(record)

    pairs = []

    for question_id, recall_records in recall_by_question.items():
        initial_records = initial_by_question.get(question_id, [])
        if len(recall_records) != 1 or len(initial_records) > 1:
            continue
        recall_record = recall_records[0]
        initial_record = initial_records[0] if initial_records else None

        if initial_record is None:
            parent_id = recall_record.get("parentRecordId")

            if parent_id is not None:
                parents = [record for record in records if record.get("recordId") == parent_id]
                initial_record = parents[0] if len(parents) == 1 else None

        if initial_record is None:
            continue

        # Do not turn inconsistent provenance into a plausible numeric score.
        source_question = initial_record.get("recallQuestionId")
        if (str(initial_record.get("answerRole") or "").upper() not in {"", "INITIAL"}
                or (source_question is not None and int(source_question) != question_id)
                or recall_record.get("parentRecordId") not in {None, initial_record.get("recordId")}):
            continue
        source_id, answer_id = initial_record.get("recordId"), recall_record.get("recordId")
        if (source_id is None or answer_id is None or source_id == answer_id
                or any(sum(row.get("recordId") == record_id for row in records) != 1
                       for record_id in (source_id, answer_id))):
            continue
        position_field = "turnOrder" if any(
            row.get("turnOrder") is not None for row in (initial_record, recall_record)
        ) else "recordId"
        try:
            source_position = int(initial_record.get(position_field) or 0)
            answer_position = int(recall_record.get(position_field) or 0)
        except (TypeError, ValueError):
            continue
        if not 0 < source_position < answer_position:
            continue

        # New records carry the outgoing question identity. Never score against
        # a different question, even if the incoming answer link says otherwise.
        presented_id = preceding.get(answer_id, {}).get("presentedRecallQuestionId")
        if presented_id is not None and str(presented_id) != str(question_id):
            continue

        pairs.append(
            (
                question_id,
                initial_record,
                recall_record,
            )
        )

    return pairs


def calculate_personal_fact_score(
    expected_answer: str,
    current_text: str,
) -> Optional[float]:
    expected_answer = clean_text(expected_answer)
    current_text = clean_text(current_text)

    if not expected_answer or not current_text:
        return None

    if expected_answer in PLACEHOLDER_EXPECTED_ANSWERS:
        return None

    if expected_answer == current_text:
        return 100.0

    if expected_answer in current_text or current_text in expected_answer:
        return 70.0

    return 0.0


def get_today_weekday_text(reference_date: date | None = None) -> str:
    today = reference_date or datetime.now(KOREAN_TIMEZONE).date()
    return KOREAN_WEEKDAYS[today.weekday()]


def calculate_weekday_score(current_text: str, reference_date: date | None = None) -> Optional[float]:
    current_text = clean_text(current_text)

    if not current_text:
        return None

    mentioned = set(re.findall(r"[월화수목금토일]요일", current_text))
    if not mentioned:
        short = re.fullmatch(r"(?:오늘은?\s*)?([월화수목금토일])(?:\s*(?:요|이요|입니다|이에요|예요))?", current_text)
        if short:
            mentioned.add(short.group(1) + "요일")
    if len(mentioned) != 1:
        return None
    return 100.0 if get_today_weekday_text(reference_date) in mentioned else 0.0


def _parse_calendar_answer(text: str) -> Optional[tuple[int | None, int, int]]:
    text = str(text or "")
    patterns = (
        r"(?<!\d)(?:(\d{4})\s*년\s*)?(\d{1,2})\s*월\s*(\d{1,2})\s*일?",
        r"(?<!\d)(?:(\d{4})[./-])?(\d{1,2})[./-](\d{1,2})(?!\d)",
    )
    answers = {tuple(int(value) if value else None for value in match.groups())
               for pattern in patterns for match in re.finditer(pattern, text)}
    return next(iter(answers)) if len(answers) == 1 else None


def extract_month_day(text: str) -> Optional[tuple[int, int]]:
    parsed = _parse_calendar_answer(text)
    return parsed[1:] if parsed else None


def calculate_date_score(current_text: str, reference_date: date | None = None) -> Optional[float]:
    parsed = _parse_calendar_answer(current_text)

    if parsed is None:
        return None

    year, month, day = parsed
    today = reference_date or datetime.now(KOREAN_TIMEZONE).date()
    years = (year,) if year is not None else (today.year - 1, today.year, today.year + 1)
    differences = []
    for candidate_year in years:
        try:
            differences.append(abs((today - date(candidate_year, month, day)).days))
        except ValueError:
            continue
    if not differences:
        return 0.0
    diff_days = min(differences)

    if diff_days == 0:
        return 100.0

    if diff_days == 1:
        return 80.0

    if diff_days <= 3:
        return 60.0

    if diff_days <= 7:
        return 40.0

    return 0.0


def calculate_orientation_score(
    expected_answer: str,
    current_text: str,
    reference_date: date | None = None,
) -> Optional[float]:
    expected_answer = str(expected_answer or "").upper()

    if expected_answer == "TODAY_WEEKDAY":
        return calculate_weekday_score(current_text, reference_date)

    if expected_answer == "TODAY_DATE":
        return calculate_date_score(current_text, reference_date)

    return calculate_personal_fact_score(
        expected_answer=expected_answer,
        current_text=current_text,
    )


def _recording_date(recorded_at) -> date | None:
    try:
        recorded = datetime.fromisoformat(str(recorded_at).replace("Z", "+00:00"))
        if recorded.tzinfo is not None:
            recorded = recorded.astimezone(KOREAN_TIMEZONE)
        return recorded.date()
    except (TypeError, ValueError, OverflowError):
        return None


def calculate_initial_fixed_score(
    records: List[dict],
    questions: Dict[int, dict],
) -> Optional[float]:
    scores = []

    for record in records:
        answer_role = str(record.get("answerRole") or "").upper()
        question_id = record.get("recallQuestionId")

        if answer_role != "FIXED" or question_id is None:
            continue

        question = questions.get(int(question_id), {})
        question_type = str(question.get("questionType") or "").upper()
        expected_answer = str(question.get("expectedAnswer") or "").strip()
        current_text = record.get("transcriptText") or ""

        score = None

        if question_type == "PERSONAL":
            score = calculate_personal_fact_score(
                expected_answer=expected_answer,
                current_text=current_text,
            )

        elif question_type == "ORIENTATION":
            reference_date = _recording_date(record.get("recordedAt"))
            if expected_answer.upper() in {"TODAY_DATE", "TODAY_WEEKDAY"} and reference_date is None:
                continue
            score = calculate_orientation_score(
                expected_answer=expected_answer,
                current_text=current_text,
                reference_date=reference_date,
            )

        if score is not None:
            scores.append(score)

    if not scores:
        return None

    return clamp_score(sum(scores) / len(scores))


def get_recall_scoring_text(
    question: dict,
    initial_record: dict,
) -> str:
    expected_answer = clean_text(question.get("expectedAnswer") or "")

    if expected_answer and expected_answer not in PLACEHOLDER_EXPECTED_ANSWERS:
        return expected_answer

    return initial_record.get("transcriptText") or ""


def combine_text_score(
    initial_fixed_score: Optional[float],
    recall_score: Optional[float],
) -> Optional[float]:
    """
    textScore는 우리 인지/회상 파트 전체 점수다.

    인지/회상 내부 비율은 기존 고정 질문 : 자연 회상 = 3 : 4를 유지한다.
    최종 합성은 음성 50%, 인지/회상 50%이므로 전체 환산 비중은
    고정 질문 약 21.43%, 자연 회상 약 28.57%다 (두 점수 모두 있을 때).

    textScore 자체는 0~100 점수로 저장한다.
    따라서 내부 비율은:
    - 초기 고정 질문: 30 / 70
    - 자연 회상 질문: 40 / 70
    """

    if initial_fixed_score is not None and recall_score is not None:
        return clamp_score(
            initial_fixed_score * (30 / 70)
            + recall_score * (40 / 70)
        )

    if initial_fixed_score is not None:
        return clamp_score(initial_fixed_score)

    if recall_score is not None:
        return clamp_score(recall_score)

    return None


def calculate_final_risk_from_text_score(
    speech_risk_score: float,
    text_score: float,
) -> dict:
    speech_risk_score = clamp_score(speech_risk_score)
    text_score = clamp_score(text_score)

    text_risk_score = clamp_score(100.0 - text_score)

    final_risk_score = (
        speech_risk_score * SPEECH_RISK_WEIGHT
        + text_risk_score * COGNITIVE_RISK_WEIGHT
    )

    final_risk_score = clamp_score(final_risk_score)

    # 서비스 운영 기준: LOW < 40, MEDIUM < 60, HIGH >= 60.
    # HIGH 경계 60은 보호자 알림 기준과 동일하게 유지한다.
    if final_risk_score < 40:
        risk_level = "LOW"
    elif final_risk_score < 60:
        risk_level = "MEDIUM"
    else:
        risk_level = "HIGH"

    return {
        "textRiskScore": text_risk_score,
        "finalRiskScore": final_risk_score,
        "riskLevel": risk_level,
    }


def analyze_session_recall(
    user_id: int,
    session_id: int,
    speech_risk_score: float,
    base_url: str = BASE_URL,
    target_recall_record_id: Optional[int] = None,
    send_all_recall_results: bool = True,
    speech_evaluation_status: str | None = None,
    records_snapshot: Optional[List[dict]] = None,
) -> None:
    records = records_snapshot if records_snapshot is not None else get_session_records(
        session_id=session_id,
        base_url=base_url,
    )

    questions = get_recall_questions(
        user_id=user_id,
        base_url=base_url,
    )

    initial_fixed_score = calculate_initial_fixed_score(
        records=records,
        questions=questions,
    )

    pairs = find_recall_pairs(records)

    final_recall_scores = []
    withheld_statuses = []
    paired_record_ids = {answer["recordId"] for _, _, answer in pairs}
    unpaired_recall_count = sum(
        str(record.get("answerRole") or "").upper() == "RECALL"
        and record.get("recordId") not in paired_record_ids
        for record in records
    )
    if unpaired_recall_count:
        withheld_statuses.append("unpaired_recall_record")

    for question_id, initial_record, recall_record in pairs:
        question = questions.get(question_id)
        if not question:
            withheld_statuses.append("missing_recall_question_metadata")
            continue

        past_text = get_recall_scoring_text(
            question=question,
            initial_record=initial_record,
        )
        current_text = recall_record.get("transcriptText") or ""

        keywords = question.get("keywords") or []
        question_type = question.get("questionType") or "DEFAULT"

        recall_scores = calculate_final_recall_score(
            past_text=past_text,
            current_text=current_text,
            keywords=keywords,
            question_type=question_type,
            target_answer=(past_text if question.get("answerType") in
                           {"FOOD", "PLACE", "PERSON", "OBJECT", "MEDIA"}
                           and question.get("expectedAnswer") not in PLACEHOLDER_EXPECTED_ANSWERS
                           and question.get("expectedAnswer") else None),
            source_text=initial_record.get("transcriptText") or "",
        )
        evaluation_status = recall_scores.get("evaluationStatus", "SCORED")
        if evaluation_status == "SCORED" and (
            not is_valid_score(recall_scores.get("finalRecallScore"))
            or any(key in recall_scores and not is_valid_score(recall_scores[key])
                   for key in ("similarityScore", "keywordScore"))
        ):
            evaluation_status = "NOT_EVALUABLE"
            recall_scores = {**recall_scores, "evaluationReasons": ["invalid_numeric_score"]}
        evaluable = evaluation_status == "SCORED"
        if not evaluable:
            withheld_statuses.append(evaluation_status)

        current_record_id = int(recall_record["recordId"])
        should_send_recall_result = (
            send_all_recall_results
            or (
                target_recall_record_id is not None
                and current_record_id == int(target_recall_record_id)
            )
        )

        if should_send_recall_result:
            recall_response = send_recall_result(
                recall_question_id=question_id,
                past_record_id=initial_record["recordId"],
                current_record_id=current_record_id,
                similarity_score=recall_scores.get("similarityScore") if evaluable else None,
                keyword_score=recall_scores.get("keywordScore") if evaluable else None,
                final_recall_score=recall_scores.get("finalRecallScore") if evaluable else None,
                evaluation_status=evaluation_status,
                evaluation_reason=", ".join(recall_scores.get("evaluationReasons") or []),
                base_url=base_url,
            )

            print(
                f"recall question {question_id} status:",
                recall_response.status_code,
                recall_response.text,
            )

            recall_response.raise_for_status()

        if evaluable:
            final_recall_scores.append(recall_scores["finalRecallScore"])

    recall_score = None

    if final_recall_scores:
        recall_score = round(
            sum(final_recall_scores) / len(final_recall_scores),
            2,
        )

    text_score = combine_text_score(
        initial_fixed_score=initial_fixed_score,
        recall_score=recall_score,
    )

    speech_available = (is_valid_score(speech_risk_score)
                        and speech_evaluation_status in {None, "SCORED", "PARTIAL"})
    risk_scores = calculate_final_risk_from_text_score(
        speech_risk_score=speech_risk_score,
        text_score=text_score,
    ) if text_score is not None and speech_available else {"finalRiskScore": None, "riskLevel": None}
    if not speech_available:
        evaluation_status = "NOT_EVALUABLE"
    elif text_score is None:
        evaluation_status = "REVIEW_REQUIRED" if "REVIEW_REQUIRED" in withheld_statuses else "NOT_EVALUABLE"
    elif (initial_fixed_score is None or recall_score is None or withheld_statuses
          or speech_evaluation_status == "PARTIAL"):
        evaluation_status = "PARTIAL"
    else:
        evaluation_status = "SCORED"
    missing = []
    if initial_fixed_score is None:
        missing.append("no_fixed_evaluation")
        if any(str(row.get("answerRole") or "").upper() == "FIXED"
               and questions.get(row.get("recallQuestionId"), {}).get("questionType") == "PERSONAL"
               and questions.get(row.get("recallQuestionId"), {}).get("expectedAnswer") in PLACEHOLDER_EXPECTED_ANSWERS
               for row in records):
            missing.append("personal_reference_not_registered")
    if recall_score is None:
        missing.append("no_recall_evaluation")
    if not speech_available:
        missing.append("invalid_or_missing_speech_score")
    elif speech_evaluation_status == "PARTIAL":
        missing.append("partial_audio_evaluation")
    missing.extend(sorted(set(withheld_statuses)))

    risk_response = send_risk_result(
        session_id=session_id,
        speech_risk_score=speech_risk_score if speech_available else None,
        text_score=text_score if speech_available else None,
        recall_score=recall_score if speech_available else None,
        final_risk_score=risk_scores["finalRiskScore"],
        risk_level=risk_scores["riskLevel"],
        base_url=base_url,
        evaluation_status=evaluation_status,
        evaluation_reason=", ".join(missing),
        evaluated_recall_count=len(final_recall_scores),
        total_recall_count=len(pairs) + unpaired_recall_count,
    )

    print("risk status:", risk_response.status_code, risk_response.text)
    risk_response.raise_for_status()

    try:
        compute_and_send_linguistic_markers(
            user_id=user_id,
            session_id=session_id,
            records=records,
            base_url=base_url,
        )
    except Exception as e:
        # 언어 지표는 위험도 점수에 반영되지 않는 부가 지표라, 실패해도
        # 회상/위험도 분석 자체를 막지 않는다.
        print(f"linguistic markers 저장 실패(무시하고 계속): {e}")

    print("분석 완료")
    print("initialFixedScore:", initial_fixed_score)
    print("recallScore:", recall_score)
    print("textScore:", text_score)
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
