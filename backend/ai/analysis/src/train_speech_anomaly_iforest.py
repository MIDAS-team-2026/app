from __future__ import annotations

import argparse
import json
import os
from datetime import datetime
from pathlib import Path
from typing import Any

import pandas as pd

from speech_anomaly_model import (
    DEFAULT_CONTAMINATION,
    FEATURE_SETS,
    MODEL_VERSION,
    RANDOM_STATE,
    calculate_raw_anomaly_scores,
    coerce_feature_frame,
    convert_raw_scores_to_0_100,
    fit_score_calibration,
    get_available_features,
    save_model_bundle,
    summarize_scores,
    train_isolation_forest,
)


AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATA_DIR = Path(os.getenv("MIDAS_EXTRACTED_DIR", AI_ANALYSIS_ROOT / "outputs"))
DEFAULT_NORMAL_CSV = DEFAULT_DATA_DIR / "elderly_chatbot_reference_features_egemaps_sample_1000.csv"
DEFAULT_ABNORMAL_CSV = DEFAULT_DATA_DIR / "dysarthria_neuro_25_segment_features_egemaps.csv"
DEFAULT_MODEL_PATH = AI_ANALYSIS_ROOT / "model" / "speech_anomaly_iforest_v2.pkl"
DEFAULT_REPORT_PATH = AI_ANALYSIS_ROOT / "model" / "speech_anomaly_iforest_v2_report.json"

METADATA_COLUMNS = {
    "json_path",
    "audio_file_name",
    "audio_path",
    "matched",
    "script_id",
    "transcript",
    "record_time",
    "record_quality",
    "record_date",
    "script_set_no",
    "record_environment",
    "collection_unit_code",
    "city_code",
    "record_unit",
    "conversation_theme",
    "gender",
    "recorder_id",
    "age",
    "_source_row_index",
    "sample_seed",
    "sample_group",
    "feature_schema_version",
    "feature_extract_range",
    "extracted_at",
    "segment_id",
    "source_dataset",
    "label",
    "label_name",
    "source_audio_file_name",
    "source_audio_path",
    "source_json_path",
    "source_row_index",
    "speaker_key",
    "segment_index",
    "segment_start_sec",
    "segment_end_sec",
    "test_method",
    "disease_type",
    "sex",
    "egemaps_available",
    "egemaps_error",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="일반 고령자 음성 기준 Isolation Forest 이상치 모델을 학습합니다."
    )
    parser.add_argument("--normal-csv", type=Path, default=DEFAULT_NORMAL_CSV)
    parser.add_argument("--abnormal-csv", type=Path, default=DEFAULT_ABNORMAL_CSV)
    parser.add_argument("--model-path", type=Path, default=DEFAULT_MODEL_PATH)
    parser.add_argument("--report-path", type=Path, default=DEFAULT_REPORT_PATH)
    parser.add_argument("--normal-train-size", type=int, default=800)
    parser.add_argument("--contamination", type=float, default=DEFAULT_CONTAMINATION)
    parser.add_argument("--random-state", type=int, default=RANDOM_STATE)
    parser.add_argument(
        "--feature-set",
        choices=[*FEATURE_SETS.keys(), "all_common_numeric", "auto"],
        default="auto",
    )
    return parser.parse_args()


def load_csv(path: Path) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"CSV not found: {path}")
    return pd.read_csv(path)


def split_normal_rows(
    normal_df: pd.DataFrame,
    train_size: int,
    random_state: int,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    if len(normal_df) < 2:
        raise ValueError("Normal CSV needs at least 2 rows.")

    train_size = min(max(1, train_size), len(normal_df) - 1)
    shuffled = normal_df.sample(frac=1.0, random_state=random_state)
    return shuffled.iloc[:train_size].copy(), shuffled.iloc[train_size:].copy()


def evaluate_feature_set(
    name: str,
    requested_features: list[str],
    normal_train_df: pd.DataFrame,
    normal_test_df: pd.DataFrame,
    abnormal_df: pd.DataFrame,
    contamination: float,
    random_state: int,
) -> dict[str, Any]:
    available_features = get_available_features(normal_train_df, requested_features)
    available_features = [
        column
        for column in available_features
        if column in abnormal_df.columns
        and pd.to_numeric(abnormal_df[column], errors="coerce").notna().any()
    ]

    if not available_features:
        raise ValueError(f"No usable features for feature set: {name}")

    X_train = coerce_feature_frame(normal_train_df, available_features)
    X_normal_test = coerce_feature_frame(normal_test_df, available_features)
    X_abnormal = coerce_feature_frame(abnormal_df, available_features)

    pipeline = train_isolation_forest(
        X_train,
        contamination=contamination,
        random_state=random_state,
    )

    raw_train = calculate_raw_anomaly_scores(pipeline, X_train)
    calibration = fit_score_calibration(raw_train)

    train_scores = convert_raw_scores_to_0_100(raw_train, calibration)
    normal_raw = calculate_raw_anomaly_scores(pipeline, X_normal_test)
    normal_scores = convert_raw_scores_to_0_100(normal_raw, calibration)
    abnormal_raw = calculate_raw_anomaly_scores(pipeline, X_abnormal)
    abnormal_scores = convert_raw_scores_to_0_100(abnormal_raw, calibration)

    normal_predictions = pipeline.predict(X_normal_test)
    abnormal_predictions = pipeline.predict(X_abnormal)

    normal_summary = summarize_scores(normal_scores, normal_predictions)
    abnormal_summary = summarize_scores(abnormal_scores, abnormal_predictions)

    mean_gap = abnormal_summary["mean"] - normal_summary["mean"]
    flag_gap = abnormal_summary["score_ge_60_rate"] - normal_summary["score_ge_60_rate"]
    selection_score = mean_gap + (flag_gap * 50.0) - (normal_summary["score_ge_60_rate"] * 20.0)

    return {
        "feature_set_name": name,
        "feature_columns": available_features,
        "feature_count": len(available_features),
        "pipeline": pipeline,
        "score_calibration": calibration,
        "normal_train_summary": summarize_scores(train_scores, pipeline.predict(X_train)),
        "normal_test_summary": normal_summary,
        "abnormal_test_summary": abnormal_summary,
        "mean_gap_abnormal_minus_normal": round(float(mean_gap), 3),
        "score_ge_60_gap": round(float(flag_gap), 4),
        "selection_score": round(float(selection_score), 3),
    }


def discover_all_common_numeric_features(
    normal_df: pd.DataFrame,
    abnormal_df: pd.DataFrame,
) -> list[str]:
    common_columns = [
        column
        for column in normal_df.columns
        if column in abnormal_df.columns and column not in METADATA_COLUMNS
    ]

    numeric_columns = []
    for column in common_columns:
        normal_values = pd.to_numeric(normal_df[column], errors="coerce")
        abnormal_values = pd.to_numeric(abnormal_df[column], errors="coerce")
        if normal_values.notna().any() and abnormal_values.notna().any():
            numeric_columns.append(column)

    return numeric_columns


def reportable_result(result: dict[str, Any]) -> dict[str, Any]:
    return {
        key: value
        for key, value in result.items()
        if key != "pipeline"
    }


def choose_best_result(results: list[dict[str, Any]]) -> dict[str, Any]:
    return max(results, key=lambda item: item["selection_score"])


def write_report(report: dict[str, Any], report_path: Path) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def main() -> None:
    args = parse_args()

    normal_df = load_csv(args.normal_csv)
    abnormal_df = load_csv(args.abnormal_csv)
    normal_train_df, normal_test_df = split_normal_rows(
        normal_df,
        train_size=args.normal_train_size,
        random_state=args.random_state,
    )

    if args.feature_set == "auto":
        candidate_feature_sets = {
            **FEATURE_SETS,
            "all_common_numeric": discover_all_common_numeric_features(
                normal_df,
                abnormal_df,
            ),
        }
    elif args.feature_set == "all_common_numeric":
        candidate_feature_sets = {
            "all_common_numeric": discover_all_common_numeric_features(
                normal_df,
                abnormal_df,
            ),
        }
    else:
        candidate_feature_sets = {
            args.feature_set: FEATURE_SETS[args.feature_set],
        }

    results = []
    for name, requested_features in candidate_feature_sets.items():
        print(f"\n[train] feature_set={name}")
        result = evaluate_feature_set(
            name=name,
            requested_features=requested_features,
            normal_train_df=normal_train_df,
            normal_test_df=normal_test_df,
            abnormal_df=abnormal_df,
            contamination=args.contamination,
            random_state=args.random_state,
        )
        print(
            "[result] "
            f"features={result['feature_count']} "
            f"normal_mean={result['normal_test_summary']['mean']} "
            f"abnormal_mean={result['abnormal_test_summary']['mean']} "
            f"gap={result['mean_gap_abnormal_minus_normal']} "
            f"normal_ge60={result['normal_test_summary']['score_ge_60_rate']} "
            f"abnormal_ge60={result['abnormal_test_summary']['score_ge_60_rate']}"
        )
        results.append(result)

    best_result = choose_best_result(results)
    model_bundle = {
        "model_version": MODEL_VERSION,
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "pipeline": best_result["pipeline"],
        "feature_set_name": best_result["feature_set_name"],
        "feature_columns": best_result["feature_columns"],
        "score_calibration": best_result["score_calibration"],
        "contamination": args.contamination,
        "score_direction": "higher_score_means_more_abnormal",
    }
    save_model_bundle(model_bundle, args.model_path)

    report = {
        "model_version": MODEL_VERSION,
        "model_path": str(args.model_path),
        "normal_csv": str(args.normal_csv),
        "abnormal_csv": str(args.abnormal_csv),
        "normal_train_count": int(len(normal_train_df)),
        "normal_test_count": int(len(normal_test_df)),
        "abnormal_test_count": int(len(abnormal_df)),
        "selected_feature_set": best_result["feature_set_name"],
        "selected_features": best_result["feature_columns"],
        "score_direction": "higher_score_means_more_abnormal",
        "score_thresholds": {
            "low": 30.0,
            "medium": 60.0,
            "high": 80.0,
        },
        "feature_set_results": [
            reportable_result(result)
            for result in results
        ],
    }
    write_report(report, args.report_path)

    print("\n[done]")
    print(f"selected_feature_set={best_result['feature_set_name']}")
    print(f"selected_feature_count={best_result['feature_count']}")
    print(f"model_path={args.model_path}")
    print(f"report_path={args.report_path}")


if __name__ == "__main__":
    main()
