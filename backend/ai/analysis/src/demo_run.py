import pandas as pd
from speech_abnormality_scoring import (
    FEATURE_COLUMNS,
    build_reference_profile,
    calculate_dysarthria_similarity,
    convert_similarity_to_abnormality_score
)


def main():
    reference_df = pd.read_csv("data/sample/dummy_audio_features.csv")

    reference_mean, reference_std = build_reference_profile(reference_df)

    sample_features = reference_df.iloc[0][FEATURE_COLUMNS].to_dict()

    similarity_result = calculate_dysarthria_similarity(
        sample_features,
        reference_mean,
        reference_std
    )

    # 임시 threshold. 실제 프로젝트에서는 reference 내부 분포 기반으로 계산.
    low_threshold = 0.49
    high_threshold = 0.52

    score_result = convert_similarity_to_abnormality_score(
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