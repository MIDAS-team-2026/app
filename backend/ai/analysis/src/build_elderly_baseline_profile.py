from pathlib import Path
import json
import os
import pandas as pd


AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = Path(os.getenv("MIDAS_EXTRACTED_DIR", AI_ANALYSIS_ROOT / "outputs"))

INPUT_CSV = Path(os.getenv("MIDAS_ELDERLY_FEATURE_CSV", OUTPUT_DIR / "elderly_chatbot_features.csv"))
OUTPUT_JSON = Path(
    os.getenv("MIDAS_ELDERLY_BASELINE_PROFILE", OUTPUT_DIR / "elderly_baseline_profile.json")
)

def build_baseline_profile(df):
    """
    노인 자유대화 전체 feature CSV에서 baseline 기준값을 생성한다.

    이 기준값은 사용자 자유채팅 답변이 일반 노인 발화 분포에서
    어느 정도 벗어나는지 판단하는 데 사용한다.
    """

    profile = {
    "profile_name": "elderly_chatbot_baseline",
    "description": "노인 자유대화 AI챗봇 데이터 기반 일반 고령자 발화 baseline",
    "data_count": int(len(df)),

    "record_time": {
        "mean": float(df["record_time_float"].mean()),
        "q25": float(df["record_time_float"].quantile(0.25)),
        "q50": float(df["record_time_float"].quantile(0.50)),
        "q75": float(df["record_time_float"].quantile(0.75)),
        "q90": float(df["record_time_float"].quantile(0.90)),
    },

    "word_count": {
        "mean": float(df["word_count"].mean()),
        "q25": float(df["word_count"].quantile(0.25)),
        "q50": float(df["word_count"].quantile(0.50)),
        "q75": float(df["word_count"].quantile(0.75)),
    },

    "char_count": {
        "mean": float(df["char_count"].mean()),
        "q25": float(df["char_count"].quantile(0.25)),
        "q50": float(df["char_count"].quantile(0.50)),
        "q75": float(df["char_count"].quantile(0.75)),
    },

    "speech_rate_word": {
        "mean": float(df["speech_rate_word"].mean()),
        "q25": float(df["speech_rate_word"].quantile(0.25)),
        "q50": float(df["speech_rate_word"].quantile(0.50)),
        "q75": float(df["speech_rate_word"].quantile(0.75)),
    },

    "speech_rate_char": {
        "mean": float(df["speech_rate_char"].mean()),
        "q25": float(df["speech_rate_char"].quantile(0.25)),
        "q50": float(df["speech_rate_char"].quantile(0.50)),
        "q75": float(df["speech_rate_char"].quantile(0.75)),
    },

    "flag_ratio": {
        "short_answer_flag": float(df["short_answer_flag"].mean()),
        "slow_speech_flag": float(df["slow_speech_flag"].mean()),
        "long_recording_flag": float(df["long_recording_flag"].mean()),
        "low_content_slow_speech_flag": float(df["low_content_slow_speech_flag"].mean()),
    },

    "thresholds": {
        "short_answer_word_count": 5,
        "slow_speech_rate_word": float(df["speech_rate_word"].quantile(0.25)),
        "slow_speech_rate_char": float(df["speech_rate_char"].quantile(0.25)),
        "long_record_time": float(df["record_time_float"].quantile(0.75)),
        "low_content_word_count": 6,
    },

    "threshold_policy": {
        "short_answer_word_count": "word_count <= 5를 짧은 답변 기준으로 사용",
        "slow_speech_rate_word": "speech_rate_word 하위 25% 값을 느린 발화 기준으로 사용",
        "slow_speech_rate_char": "speech_rate_char 하위 25% 값을 보조 기준으로 저장",
        "long_record_time": "record_time_float 상위 25% 값을 긴 발화 기준으로 사용",
        "low_content_word_count": "word_count <= 6을 낮은 정보량 기준으로 사용"
    }
}

    optional_metric_columns = [
        "pause_count",
        "total_pause_duration",
        "avg_pause_duration",
        "max_pause_duration",
        "pause_ratio",
        "response_latency",
        "voice_activity_ratio",
    ]

    for column in optional_metric_columns:
        if column not in df.columns:
            continue

        series = pd.to_numeric(df[column], errors="coerce").dropna()
        if series.empty:
            continue

        profile[column] = {
            "mean": float(series.mean()),
            "q25": float(series.quantile(0.25)),
            "q50": float(series.quantile(0.50)),
            "q75": float(series.quantile(0.75)),
            "q90": float(series.quantile(0.90)),
        }

    optional_thresholds = {
        "high_pause_count": ("pause_count", 0.75),
        "long_total_pause_duration": ("total_pause_duration", 0.75),
        "long_avg_pause_duration": ("avg_pause_duration", 0.75),
        "long_max_pause_duration": ("max_pause_duration", 0.75),
        "high_pause_ratio": ("pause_ratio", 0.75),
        "long_response_latency": ("response_latency", 0.75),
    }

    for threshold_name, (column, quantile) in optional_thresholds.items():
        if column in df.columns:
            series = pd.to_numeric(df[column], errors="coerce").dropna()
            if not series.empty:
                profile["thresholds"][threshold_name] = float(series.quantile(quantile))

    return profile


def main():
    print("노인 자유대화 baseline profile 생성 시작")

    df = pd.read_csv(
        INPUT_CSV,
        dtype={"recorder_id": str},
        low_memory=False
    )

    print("CSV 로드 완료:", len(df))

    profile = build_baseline_profile(df)

    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)

    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(profile, f, ensure_ascii=False, indent=2)

    print("baseline profile 저장 완료:", OUTPUT_JSON)

    print("\n사용 기준값")
    print(profile["thresholds"])

    print("\nflag 비율")
    print(profile["flag_ratio"])


if __name__ == "__main__":
    main()
