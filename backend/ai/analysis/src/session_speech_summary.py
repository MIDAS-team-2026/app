from typing import List, Dict, Any


def safe_avg(values):
    """
    숫자 리스트의 평균을 안전하게 계산한다.
    """
    valid_values = [v for v in values if v is not None]

    if len(valid_values) == 0:
        return 0.0

    return sum(valid_values) / len(valid_values)


def calculate_ratio(values):
    """
    0/1 flag 리스트의 비율을 계산한다.
    """
    if len(values) == 0:
        return 0.0

    return sum(values) / len(values)


def convert_score_to_level(score: float) -> str:
    """
    0~100 점수를 Low / Medium / High로 변환한다.
    """
    if score >= 70:
        return "High"
    elif score >= 40:
        return "Medium"
    else:
        return "Low"


def summarize_session_speech(
    session_id: int,
    turn_results: List[Dict[str, Any]]
) -> Dict[str, Any]:
    """
    사용자 답변 여러 개의 분석 결과를 세션 단위 speech risk 결과로 요약한다.

    입력:
    - session_id: 세션 ID
    - turn_results: user_turn_analysis.py의 analyze_user_turn() 결과 리스트

    출력:
    - 세션 단위 speechRiskScore
    - 세션 단위 speechRiskLevel
    - 평균 말속도, 짧은 답변 비율 등 요약 지표
    """

    if len(turn_results) == 0:
        return {
            "sessionId": session_id,
            "totalUserRecords": 0,
            "speechRiskScore": 0.0,
            "speechRiskLevel": "Low",
            "message": "분석할 사용자 답변이 없습니다."
        }

    total_records = len(turn_results)

    speech_rate_values = [
        r.get("speech_rate_word") for r in turn_results
        if r.get("speech_rate_word") is not None
    ]

    short_answer_flags = [
        r.get("short_answer_flag", 0) for r in turn_results
    ]

    slow_speech_flags = [
        r.get("slow_speech_flag", 0) for r in turn_results
    ]

    low_content_slow_flags = [
        r.get("low_content_slow_speech_flag", 0) for r in turn_results
    ]

    baseline_scores = [
        r.get("baseline_speech_score", 0) for r in turn_results
    ]

    speech_abnormality_scores = [
        r.get("speech_abnormality_score", 0) for r in turn_results
    ]

    turn_speech_risk_scores = [
        r.get("speechRiskScore", 0) for r in turn_results
    ]

    dysarthria_similarity_scores = [
        r.get("dysarthria_similarity_score") for r in turn_results
        if r.get("dysarthria_similarity_score") is not None
    ]

    avg_speech_rate_word = safe_avg(speech_rate_values)

    short_answer_ratio = calculate_ratio(short_answer_flags)
    slow_speech_ratio = calculate_ratio(slow_speech_flags)
    low_content_slow_speech_ratio = calculate_ratio(low_content_slow_flags)

    avg_baseline_speech_score = safe_avg(baseline_scores)
    max_baseline_speech_score = max(baseline_scores) if baseline_scores else 0

    avg_speech_abnormality_score = safe_avg(speech_abnormality_scores)
    max_speech_abnormality_score = (
        max(speech_abnormality_scores) if speech_abnormality_scores else 0
    )

    avg_turn_speech_risk_score = safe_avg(turn_speech_risk_scores)
    max_turn_speech_risk_score = (
        max(turn_speech_risk_scores) if turn_speech_risk_scores else 0
    )

    avg_dysarthria_similarity_score = safe_avg(dysarthria_similarity_scores)

    # 세션 단위 점수는 평균과 최대값을 섞어서 계산한다.
    # 평균: 반복적으로 나타나는 문제 반영
    # 최대값: 특정 답변에서 강하게 나타난 이상 신호 반영
    speech_risk_score = (
        avg_turn_speech_risk_score * 0.7
        + max_turn_speech_risk_score * 0.3
    )

    speech_risk_score = round(speech_risk_score, 2)
    speech_risk_level = convert_score_to_level(speech_risk_score)

    return {
        "sessionId": session_id,
        "totalUserRecords": total_records,

        "avgSpeechRateWord": round(avg_speech_rate_word, 3),
        "shortAnswerRatio": round(short_answer_ratio, 3),
        "slowSpeechRatio": round(slow_speech_ratio, 3),
        "lowContentSlowSpeechRatio": round(low_content_slow_speech_ratio, 3),

        "avgBaselineSpeechScore": round(avg_baseline_speech_score, 3),
        "maxBaselineSpeechScore": max_baseline_speech_score,

        "avgSpeechAbnormalityScore": round(avg_speech_abnormality_score, 3),
        "maxSpeechAbnormalityScore": max_speech_abnormality_score,

        "avgDysarthriaSimilarityScore": round(avg_dysarthria_similarity_score, 3),

        "avgTurnSpeechRiskScore": round(avg_turn_speech_risk_score, 3),
        "maxTurnSpeechRiskScore": max_turn_speech_risk_score,

        "speechRiskScore": speech_risk_score,
        "speechRiskLevel": speech_risk_level,
    }


def main():
    """
    테스트용 실행 예시.
    실제 앱에서는 여러 record 분석 결과 리스트를 받아서 사용한다.
    """

    sample_turn_results = [
        {
            "recordId": 1,
            "speech_rate_word": 0.286,
            "short_answer_flag": 1,
            "slow_speech_flag": 1,
            "low_content_slow_speech_flag": 1,
            "baseline_speech_score": 25,
            "speech_abnormality_score": 0,
            "dysarthria_similarity_score": None,
            "speechRiskScore": 62.5,
        },
        {
            "recordId": 2,
            "speech_rate_word": 1.4,
            "short_answer_flag": 0,
            "slow_speech_flag": 0,
            "low_content_slow_speech_flag": 0,
            "baseline_speech_score": 0,
            "speech_abnormality_score": 7,
            "dysarthria_similarity_score": 0.52,
            "speechRiskScore": 17.5,
        },
        {
            "recordId": 3,
            "speech_rate_word": 0.9,
            "short_answer_flag": 0,
            "slow_speech_flag": 1,
            "low_content_slow_speech_flag": 0,
            "baseline_speech_score": 10,
            "speech_abnormality_score": 15,
            "dysarthria_similarity_score": 0.61,
            "speechRiskScore": 62.5,
        },
    ]

    result = summarize_session_speech(
        session_id=1,
        turn_results=sample_turn_results
    )

    print("세션 음성 분석 요약 결과")
    print(result)


if __name__ == "__main__":
    main()