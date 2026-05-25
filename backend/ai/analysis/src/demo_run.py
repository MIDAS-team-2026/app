from pathlib import Path
import pandas as pd

from speech_abnormality_scoring import (
    FEATURE_COLUMNS,
    build_reference_profile,
    calculate_dysarthria_similarity,
    convert_similarity_to_reference_score,
)


def main():
    """
    실제 AI-Hub 데이터 없이 더미 feature CSV를 사용해
    reference similarity scoring 흐름이 정상 작동하는지 확인하는 데모 파일입니다.

    이 데모는 실제 진단 모델이 아니라 코드 실행 확인용입니다.
    """

    
    BASE_DIR = Path(__file__).resolve().parents[1]

    sample_csv_path = BASE_DIR / "data" / "sample" / "dummy_audio_features.csv"

    print("샘플 CSV 경로:", sample_csv_path)
    print("샘플 CSV 존재 여부:", sample_csv_path.exists())

    reference_df = pd.read_csv(sample_csv_path)

    # 더미 데이터로 reference profile 생성
    reference_mean, reference_std = build_reference_profile(reference_df)

    # 첫 번째 샘플을 테스트 음성처럼 사용
    sample_features = reference_df.iloc[0][FEATURE_COLUMNS].to_dict()

    similarity_result = calculate_dysarthria_similarity(
        sample_features,
        reference_mean,
        reference_std
    )

    # 더미 데모용 임시 threshold
    low_threshold = 0.49
    high_threshold = 0.52

    score_result = convert_similarity_to_reference_score(
        similarity_result["dysarthria_similarity_score"],
        low_threshold,
        high_threshold
    )

    result = {
        **similarity_result,
        **score_result
    }

    print(result)


if __name__ == "__main__":
    main()