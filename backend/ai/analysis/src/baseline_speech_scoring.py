from pathlib import Path
import json
import os


DEFAULT_THRESHOLDS = {
    "short_answer_word_count": 5,
    "slow_speech_rate_word": 1.117,
    "slow_speech_rate_char": 3.103,
    "long_record_time": 6.4,
    "low_content_word_count": 6,
}

AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PROFILE_PATH = AI_ANALYSIS_ROOT / "config" / "elderly_baseline_profile.default.json"


def get_baseline_profile_path():
    env_path = os.getenv("MIDAS_ELDERLY_BASELINE_PROFILE")
    if env_path:
        return Path(env_path)
    return DEFAULT_PROFILE_PATH


def load_baseline_profile(profile_path=None):
    if profile_path is None:
        profile_path = get_baseline_profile_path()

    profile_path = Path(profile_path)

    if not profile_path.exists():
        print(
            f"[WARN] baseline profile 없음. 기본 threshold 사용: {profile_path}"
        )

        return {
            "thresholds": DEFAULT_THRESHOLDS,
            "source": "default_fallback",
        }

    with open(profile_path, "r", encoding="utf-8") as f:
        profile = json.load(f)

    return profile


def calculate_baseline_speech_score(
    word_count,
    speech_rate_word,
    record_time_float,
    thresholds
):
    """
    노인 자유대화 baseline 기준으로 사용자 답변 1개의 발화 위험 점수를 계산한다.

    이 점수는 의학적 진단 결과가 아니라,
    일반 고령자 자유대화 발화 분포에서 벗어나는 정도를 나타내는 보조 지표이다.
    """

    score = 0
    reasons = []

    # 1. 짧은 답변 여부
    if word_count <= thresholds["short_answer_word_count"]:
        score += 5
        reasons.append("답변 단어 수가 일반 노인 자유대화 기준보다 짧음")

    # 2. 느린 발화 여부
    if speech_rate_word < thresholds["slow_speech_rate_word"]:
        score += 10
        reasons.append("초당 단어 수가 일반 노인 자유대화 기준보다 낮음")

    # 3. 낮은 정보량 + 긴 발화 여부
    if (
        word_count <= thresholds["low_content_word_count"]
        and record_time_float > thresholds["long_record_time"]
    ):
        score += 10
        reasons.append("답변 내용은 짧지만 발화 시간이 일반 기준보다 김")

    if score >= 20:
        level = "High"
    elif score >= 10:
        level = "Medium"
    else:
        level = "Low"

    return {
        "baseline_speech_score": score,
        "baseline_speech_level": level,
        "baseline_reasons": reasons,
    }


def analyze_baseline_from_features(
    word_count,
    speech_rate_word,
    record_time_float,
    profile_path=None
):
    """
    baseline profile을 불러온 뒤,
    사용자 답변 특징값을 기준으로 baseline 점수를 계산한다.
    """

    profile = load_baseline_profile(profile_path)
    thresholds = profile["thresholds"]

    return calculate_baseline_speech_score(
        word_count=word_count,
        speech_rate_word=speech_rate_word,
        record_time_float=record_time_float,
        thresholds=thresholds
    )


def main():
    """
    간단한 테스트 실행용.
    실제 앱에서는 사용자 답변의 word_count, speech_rate_word, record_time_float를 넣어 호출한다.
    """

    # 예시: 짧고 느린 답변
    test_result = analyze_baseline_from_features(
        word_count=4,
        speech_rate_word=0.8,
        record_time_float=7.0
    )

    print("테스트 결과")
    print(test_result)


if __name__ == "__main__":
    main()
