from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

from audio_features import extract_audio_features
from speech_anomaly_model import load_model_bundle, predict_speech_anomaly_from_features


AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MODEL_PATH = AI_ANALYSIS_ROOT / "model" / "speech_anomaly_iforest_v2.pkl"

DISPLAY_FEATURES = [
    "audio_duration",
    "segment_count",
    "pause_count",
    "total_pause_duration",
    "avg_pause_duration",
    "max_pause_duration",
    "pause_ratio",
    "response_latency",
    "speech_duration",
    "voice_activity_ratio",
    "f0_semitone_mean",
    "jitter_local",
    "shimmer_local_db",
    "hnr_db",
    "voiced_segments_per_sec",
    "mean_unvoiced_segment_length",
    "voice_break_ratio",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="음성 파일 1개에서 feature를 추출하고 발화 이상 점수를 출력합니다."
    )
    parser.add_argument("audio_path", type=Path)
    parser.add_argument("--model-path", type=Path, default=DEFAULT_MODEL_PATH)
    return parser.parse_args()


def _format_value(value: Any) -> str:
    if isinstance(value, float):
        return f"{value:.4f}"
    return str(value)


def _score_to_15(score: float) -> float:
    return round((score / 100.0) * 15.0, 2)


def print_analysis_result(
    audio_path: Path,
    features: dict[str, Any],
    prediction: dict[str, Any],
) -> None:
    abnormal_score = float(prediction["speech_abnormality_score"])
    model_feature_count = int(prediction["feature_count"])
    extracted_feature_count = len(
        [
            key
            for key, value in features.items()
            if isinstance(value, (int, float)) and value is not None
        ]
    )

    print("[분석 결과]")
    print(f"- 음성 파일: {audio_path}")
    print(f"- 이상치 점수 (0~100점)       : {abnormal_score:.2f} 점")
    print(f"- 구음장애 위험도 점수 (0~15점): {_score_to_15(abnormal_score):.2f} 점")
    print(f"- 위험도 레벨 (Level)          : {prediction['speech_abnormality_level']}")
    print(f"- Isolation Forest 예측        : {prediction['iforest_prediction']}")
    print("-" * 58)
    print(f"- 모델이 사용한 피처 수        : {model_feature_count}개")
    print(f"- 추출된 전체 숫자 피처 수     : {extracted_feature_count}개")
    print(f"- 선택된 피처 묶음             : {prediction.get('feature_set_name')}")
    print("- [추출된 피처 값 샘플 확인]")

    shown_count = 0
    for key in DISPLAY_FEATURES:
        if key in features and features[key] is not None:
            print(f"  * {key}: {_format_value(features[key])}")
            shown_count += 1

    hidden_count = max(0, extracted_feature_count - shown_count)
    print(f"  * ... 외 {hidden_count}개 항목 정상 추출됨")
    print("=" * 58)


def main() -> None:
    args = parse_args()
    audio_path = args.audio_path

    if not audio_path.exists():
        raise FileNotFoundError(f"음성 파일을 찾을 수 없습니다: {audio_path}")

    model_bundle = load_model_bundle(args.model_path)
    features = extract_audio_features(str(audio_path))
    if features is None:
        raise RuntimeError("음향 feature 추출에 실패했습니다.")

    prediction = predict_speech_anomaly_from_features(features, model_bundle)
    print_analysis_result(audio_path, features, prediction)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"[오류] {exc}")
        sys.exit(1)
