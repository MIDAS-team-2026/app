from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import RobustScaler


CORE_VOICE_QUALITY_FEATURES = [
    "pause_ratio",
    "voice_break_ratio",
    "voiced_segments_per_sec",
    "mean_unvoiced_segment_length",
    "response_latency",
    "jitter_local",
    "hnr_db",
    "shimmer_local_db",
]

BASIC_ACOUSTIC_FEATURES = [
    "rms_mean",
    "rms_std",
    "zcr_mean",
    "zcr_std",
    "spectral_centroid_mean",
    "spectral_centroid_std",
]

MFCC_FEATURES = [
    *[f"mfcc_{i}_mean" for i in range(1, 14)],
    *[f"mfcc_{i}_std" for i in range(1, 14)],
]

FEATURE_SETS = {
    "core_voice_quality": CORE_VOICE_QUALITY_FEATURES,
    "core_plus_basic_acoustic": CORE_VOICE_QUALITY_FEATURES + BASIC_ACOUSTIC_FEATURES,
    "core_plus_mfcc": CORE_VOICE_QUALITY_FEATURES + MFCC_FEATURES,
    "core_plus_basic_mfcc": CORE_VOICE_QUALITY_FEATURES
    + BASIC_ACOUSTIC_FEATURES
    + MFCC_FEATURES,
}

MODEL_VERSION = "speech_anomaly_iforest_v2"
DEFAULT_CONTAMINATION = 0.05
RANDOM_STATE = 42


def get_available_features(df: pd.DataFrame, requested_columns: list[str]) -> list[str]:
    return [
        column
        for column in requested_columns
        if column in df.columns
        and pd.to_numeric(df[column], errors="coerce").notna().any()
    ]


def coerce_feature_frame(df: pd.DataFrame, feature_columns: list[str]) -> pd.DataFrame:
    features = df.reindex(columns=feature_columns).copy()
    features = features.apply(pd.to_numeric, errors="coerce")
    return features.replace([np.inf, -np.inf], np.nan)


def train_isolation_forest(
    features: pd.DataFrame,
    contamination: float = DEFAULT_CONTAMINATION,
    random_state: int = RANDOM_STATE,
) -> Pipeline:
    pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", RobustScaler()),
            (
                "model",
                IsolationForest(
                    n_estimators=400,
                    max_samples="auto",
                    contamination=contamination,
                    random_state=random_state,
                    n_jobs=-1,
                ),
            ),
        ]
    )
    pipeline.fit(features)
    return pipeline


def calculate_raw_anomaly_scores(pipeline: Pipeline, features: pd.DataFrame) -> np.ndarray:
    return -pipeline.decision_function(features)


def fit_score_calibration(raw_scores: np.ndarray) -> dict[str, float]:
    raw_scores = np.asarray(raw_scores, dtype=float)
    q50 = float(np.nanquantile(raw_scores, 0.50))
    q95 = float(np.nanquantile(raw_scores, 0.95))
    q99 = float(np.nanquantile(raw_scores, 0.99))

    if q95 <= q50:
        q95 = q50 + 1e-6
    if q99 <= q95:
        q99 = q95 + 1e-6

    return {
        "raw_score_p50": q50,
        "raw_score_p95": q95,
        "raw_score_p99": q99,
        "low_threshold": 30.0,
        "medium_threshold": 60.0,
        "high_threshold": 80.0,
    }


def convert_raw_scores_to_0_100(
    raw_scores: np.ndarray,
    calibration: dict[str, float],
) -> np.ndarray:
    raw_scores = np.asarray(raw_scores, dtype=float)
    q50 = calibration["raw_score_p50"]
    q95 = calibration["raw_score_p95"]
    q99 = calibration["raw_score_p99"]

    scores = np.zeros_like(raw_scores, dtype=float)

    mid_mask = (raw_scores > q50) & (raw_scores <= q95)
    scores[mid_mask] = ((raw_scores[mid_mask] - q50) / (q95 - q50)) * 60.0

    high_mask = raw_scores > q95
    scores[high_mask] = 60.0 + ((raw_scores[high_mask] - q95) / (q99 - q95)) * 40.0

    return np.clip(scores, 0.0, 100.0)


def summarize_scores(scores: np.ndarray, predictions: np.ndarray | None = None) -> dict[str, Any]:
    scores = np.asarray(scores, dtype=float)
    summary = {
        "count": int(len(scores)),
        "mean": round(float(np.nanmean(scores)), 3),
        "std": round(float(np.nanstd(scores)), 3),
        "p25": round(float(np.nanquantile(scores, 0.25)), 3),
        "median": round(float(np.nanquantile(scores, 0.50)), 3),
        "p75": round(float(np.nanquantile(scores, 0.75)), 3),
        "p95": round(float(np.nanquantile(scores, 0.95)), 3),
        "min": round(float(np.nanmin(scores)), 3),
        "max": round(float(np.nanmax(scores)), 3),
        "score_ge_30_rate": round(float(np.mean(scores >= 30.0)), 4),
        "score_ge_60_rate": round(float(np.mean(scores >= 60.0)), 4),
        "score_ge_80_rate": round(float(np.mean(scores >= 80.0)), 4),
    }

    if predictions is not None:
        predictions = np.asarray(predictions)
        summary["iforest_outlier_rate"] = round(float(np.mean(predictions == -1)), 4)

    return summary


def score_to_level(score: float) -> str:
    if score >= 80.0:
        return "High"
    if score >= 60.0:
        return "Medium"
    if score >= 30.0:
        return "Low"
    return "Minimal"


def predict_speech_anomaly_from_features(
    feature_dict: dict[str, Any],
    model_bundle: dict[str, Any],
) -> dict[str, Any]:
    feature_columns = model_bundle["feature_columns"]
    calibration = model_bundle["score_calibration"]
    pipeline = model_bundle["pipeline"]

    features = coerce_feature_frame(pd.DataFrame([feature_dict]), feature_columns)
    raw_score = calculate_raw_anomaly_scores(pipeline, features)
    score = convert_raw_scores_to_0_100(raw_score, calibration)
    prediction = pipeline.predict(features)

    return {
        "speech_abnormality_score": round(float(score[0]), 3),
        "speech_abnormality_level": score_to_level(float(score[0])),
        "raw_anomaly_score": round(float(raw_score[0]), 6),
        "iforest_prediction": int(prediction[0]),
        "feature_set_name": model_bundle.get("feature_set_name"),
        "feature_count": len(feature_columns),
    }


def save_model_bundle(model_bundle: dict[str, Any], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model_bundle, output_path)


def load_model_bundle(model_path: Path) -> dict[str, Any]:
    return joblib.load(model_path)
