from pathlib import Path
import os
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split

from speech_abnormality_scoring import (
    FEATURE_COLUMNS,
    build_reference_profile,
    calculate_dysarthria_similarity,
    calculate_dynamic_thresholds,
    convert_similarity_to_reference_score,
)


AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = Path(os.getenv("MIDAS_EXTRACTED_DIR", AI_ANALYSIS_ROOT / "outputs"))

FEATURE_CSV = Path(
    os.getenv("MIDAS_REFERENCE_FEATURE_CSV", OUTPUT_DIR / "dysarthria_25_reference_features.csv")
)
MODEL_DIR = Path(os.getenv("MIDAS_REFERENCE_MODEL_DIR", AI_ANALYSIS_ROOT / "model"))
MODEL_DIR.mkdir(parents=True, exist_ok=True)


def main():
    reference_df = pd.read_csv(FEATURE_CSV)

    print("전체 reference feature 수:", len(reference_df))

    ref_train_df, ref_test_df = train_test_split(
        reference_df,
        test_size=0.2,
        random_state=42
    )

    print("reference profile 생성용:", len(ref_train_df))
    print("내부 테스트용:", len(ref_test_df))

    reference_mean, reference_std = build_reference_profile(ref_train_df)

    test_results = []

    for idx, row in ref_test_df.iterrows():
        sample_features = row[FEATURE_COLUMNS].to_dict()

        similarity_result = calculate_dysarthria_similarity(
            sample_features,
            reference_mean,
            reference_std
        )

        test_results.append({
            "audio_file_name": row.get("audio_file_name", ""),
            **similarity_result
        })

    test_result_df = pd.DataFrame(test_results)

    print("\n내부 테스트 유사도 통계")
    print(test_result_df["dysarthria_similarity_score"].describe())

    low_threshold, high_threshold = calculate_dynamic_thresholds(
        test_result_df["dysarthria_similarity_score"]
    )

    print("\n동적 threshold")
    print("low_threshold:", low_threshold)
    print("high_threshold:", high_threshold)

    test_result_df["reference_similarity_level"] = test_result_df[
        "dysarthria_similarity_score"
    ].apply(
        lambda x: convert_similarity_to_reference_score(
            x,
            low_threshold,
            high_threshold
        )["reference_similarity_level"]
    )

    test_result_df["reference_similarity_score"] = test_result_df[
        "dysarthria_similarity_score"
    ].apply(
        lambda x: convert_similarity_to_reference_score(
            x,
            low_threshold,
            high_threshold
        )["reference_similarity_score"]
    )

    print("\n레벨 분포")
    print(test_result_df["reference_similarity_level"].value_counts())

    joblib.dump(reference_mean, MODEL_DIR / "reference_mean.pkl")
    joblib.dump(reference_std, MODEL_DIR / "reference_std.pkl")
    joblib.dump(
        {
            "low_threshold": low_threshold,
            "high_threshold": high_threshold
        },
        MODEL_DIR / "thresholds.pkl"
    )

    test_result_df.to_csv(
        MODEL_DIR / "reference_internal_test_results.csv",
        index=False,
        encoding="utf-8-sig"
    )

    print("\n저장 완료")
    print("reference_mean:", MODEL_DIR / "reference_mean.pkl")
    print("reference_std:", MODEL_DIR / "reference_std.pkl")
    print("thresholds:", MODEL_DIR / "thresholds.pkl")
    print("internal_test_results:", MODEL_DIR / "reference_internal_test_results.csv")


if __name__ == "__main__":
    main()
