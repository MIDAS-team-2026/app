from pathlib import Path
import os
import joblib

from audio_features import extract_audio_features
from speech_abnormality_scoring import (
    calculate_dysarthria_similarity,
    convert_similarity_to_reference_score,
)


AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = Path(os.getenv("MIDAS_REFERENCE_MODEL_DIR", AI_ANALYSIS_ROOT / "model"))


def predict_speech_abnormality(audio_path):
    """
    실제 wav 파일 하나를 입력받아 발화 이상 참고군 유사도와 점수를 계산합니다.

    반환값은 정상/비정상 진단 결과가 아니라,
    25.언어+뇌신경장애 참고군과의 음향적 유사도 기반 결과입니다.
    """

    reference_mean = joblib.load(MODEL_DIR / "reference_mean.pkl")
    reference_std = joblib.load(MODEL_DIR / "reference_std.pkl")
    thresholds = joblib.load(MODEL_DIR / "thresholds.pkl")

    sample_features = extract_audio_features(audio_path)

    if sample_features is None:
        return {
            "error": "음향 특징 추출에 실패했습니다."
        }

    similarity_result = calculate_dysarthria_similarity(
        sample_features,
        reference_mean,
        reference_std
    )

    score_result = convert_similarity_to_reference_score(
        similarity_result["dysarthria_similarity_score"],
        thresholds["low_threshold"],
        thresholds["high_threshold"]
    )

    return {
        **similarity_result,
        **score_result
    }


if __name__ == "__main__":
    test_audio_path = os.getenv("MIDAS_TEST_AUDIO_PATH")
    if not test_audio_path:
        raise ValueError("MIDAS_TEST_AUDIO_PATH 환경변수에 테스트 음성 파일 경로를 넣어주세요.")

    result = predict_speech_abnormality(test_audio_path)

    print(result)
