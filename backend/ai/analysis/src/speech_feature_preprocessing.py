from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd


MIN_SEGMENT_DURATION_SEC = 4.0
MIN_VOICE_ACTIVITY_RATIO = 0.1
RMS_MIN_THRESHOLD = 1e-5
MAX_FEATURE_MISSING_RATIO = 0.3
IQR_MULTIPLIER = 3.0


def _as_numeric_frame(df: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    numeric = df.reindex(columns=columns).apply(pd.to_numeric, errors="coerce")
    return numeric.replace([np.inf, -np.inf], np.nan)


def _is_true_series(series: pd.Series) -> pd.Series:
    normalized = series.astype(str).str.strip().str.lower()
    return normalized.isin({"true", "1", "yes", "y"})


def _apply_abnormal_segment_quality_filter(
    df: pd.DataFrame,
    feature_columns: list[str],
) -> tuple[pd.DataFrame, dict[str, Any]]:
    original_count = len(df)
    keep_mask = pd.Series(True, index=df.index)
    removed_by_rule: dict[str, int] = {}

    # 구음장애 데이터는 원래 긴 낭독 음성을 8초 단위로 잘라 만든 데이터다.
    # 그래서 자르는 과정에서 말이 거의 안 들어간 조각이나, 너무 짧은 조각,
    # 녹음 에너지가 거의 없는 조각이 섞일 수 있다.
    # 이런 샘플은 구음장애 특성이라기보다 segment 생성 과정에서 생긴 노이즈일 수 있으므로
    # RandomForest 학습 전에 먼저 걸러준다.
    #
    # 참고:
    # - Daza Santacoloma et al. (2009)는 voice pathology detection 전처리에서
    #   outlier detection, normality verification, distribution transformation의 필요성을 제시했다.
    # - Silva et al. (2019)는 voice pathology recognition에서 boxplot/std 기반 이상값 탐지와
    #   경계값 대체 방식의 outlier treatment가 성능 개선에 도움이 될 수 있음을 보였다.
    #
    # - segment_duration < 4.0:
    #   원래 8초짜리로 맞춰 자른 건데, 절반도 안 되는 조각이면 안정적인 음성 특징을 보기 어렵다.
    #
    # - voice_activity_ratio < 0.1:
    #   8초 중 실제 말소리가 10%도 안 들어 있으면 발화 데이터라기보다 무음/잡음 조각일 가능성이 높다.
    #
    # - rms_mean <= RMS_MIN_THRESHOLD:
    #   RMS 에너지가 거의 0이면 소리가 거의 없거나 녹음이 제대로 안 된 조각일 가능성이 있다.

    if "egemaps_available" in df.columns:
        condition = _is_true_series(df["egemaps_available"])
        removed_by_rule["egemaps_unavailable"] = int((keep_mask & ~condition).sum())
        keep_mask &= condition

    if "segment_duration" in df.columns:
        values = pd.to_numeric(df["segment_duration"], errors="coerce")
        condition = values >= MIN_SEGMENT_DURATION_SEC
        removed_by_rule["short_segment_duration"] = int((keep_mask & ~condition).sum())
        keep_mask &= condition

    if "voice_activity_ratio" in df.columns:
        values = pd.to_numeric(df["voice_activity_ratio"], errors="coerce")
        condition = values >= MIN_VOICE_ACTIVITY_RATIO
        removed_by_rule["low_voice_activity_ratio"] = int((keep_mask & ~condition).sum())
        keep_mask &= condition

    if "rms_mean" in df.columns:
        values = pd.to_numeric(df["rms_mean"], errors="coerce")
        condition = values > RMS_MIN_THRESHOLD
        removed_by_rule["near_silent_rms"] = int((keep_mask & ~condition).sum())
        keep_mask &= condition

    if feature_columns:
        feature_frame = _as_numeric_frame(df, feature_columns)
        missing_ratio = feature_frame.isna().mean(axis=1)
        condition = missing_ratio <= MAX_FEATURE_MISSING_RATIO
        removed_by_rule["high_feature_missing_ratio"] = int((keep_mask & ~condition).sum())
        keep_mask &= condition

    filtered = df.loc[keep_mask].copy()

    report = {
        "original_count": int(original_count),
        "filtered_count": int(len(filtered)),
        "removed_count": int(original_count - len(filtered)),
        "removed_by_rule": removed_by_rule,
        "quality_thresholds": {
            "min_segment_duration_sec": MIN_SEGMENT_DURATION_SEC,
            "min_voice_activity_ratio": MIN_VOICE_ACTIVITY_RATIO,
            "rms_min_threshold": RMS_MIN_THRESHOLD,
            "max_feature_missing_ratio": MAX_FEATURE_MISSING_RATIO,
        },
    }

    return filtered, report


def _winsorize_and_impute_abnormal_features(
    df: pd.DataFrame,
    feature_columns: list[str],
) -> tuple[pd.DataFrame, dict[str, Any]]:
    if not feature_columns:
        return df.copy(), {
            "feature_count": 0,
            "total_clipped_values": 0,
            "total_imputed_values": 0,
            "iqr_multiplier": IQR_MULTIPLIER,
            "features": {},
        }

    processed = df.copy()
    numeric = _as_numeric_frame(processed, feature_columns)
    feature_reports: dict[str, dict[str, Any]] = {}
    total_clipped = 0
    total_imputed = 0

    # feature별 극단값은 IQR 기준으로 clipping한다.
    # voice pathology recognition 연구에서 boxplot 기반 outlier treatment가 사용된 것처럼,
    # 너무 튀는 값을 아예 삭제하지 않고 학습 가능한 범위의 경계값으로 눌러준다.
    # 이렇게 하면 병리적 음성의 특성을 완전히 지우지 않으면서,
    # 극단적인 segment 하나가 RandomForest 학습을 과하게 흔드는 것을 줄일 수 있다.
    #
    # 결측값은 평균이 아니라 median으로 채운다.
    # 음성 feature는 무음/잡음/병리적 발화 때문에 분포가 비대칭이고 극단값이 많을 수 있어서,
    # 평균보다 중앙값이 더 안정적인 대표값이다.
    for column in feature_columns:
        values = numeric[column]
        valid_values = values.dropna()
        missing_count = int(values.isna().sum())

        if valid_values.empty:
            feature_reports[column] = {
                "skipped": True,
                "reason": "all values are missing",
                "missing_count": missing_count,
            }
            continue

        q1 = float(valid_values.quantile(0.25))
        q3 = float(valid_values.quantile(0.75))
        iqr = q3 - q1

        if iqr == 0 or not np.isfinite(iqr):
            lower = q1
            upper = q3
        else:
            lower = q1 - (IQR_MULTIPLIER * iqr)
            upper = q3 + (IQR_MULTIPLIER * iqr)

        clipped = values.clip(lower=lower, upper=upper)
        clipped_count = int(((values < lower) | (values > upper)).fillna(False).sum())
        median = float(clipped.dropna().median()) if clipped.notna().any() else 0.0
        processed[column] = clipped.fillna(median)

        total_clipped += clipped_count
        total_imputed += missing_count
        feature_reports[column] = {
            "q1": round(q1, 6),
            "q3": round(q3, 6),
            "lower": round(float(lower), 6),
            "upper": round(float(upper), 6),
            "median": round(median, 6),
            "clipped_count": clipped_count,
            "imputed_count": missing_count,
        }

    report = {
        "feature_count": int(len(feature_columns)),
        "total_clipped_values": int(total_clipped),
        "total_imputed_values": int(total_imputed),
        "iqr_multiplier": IQR_MULTIPLIER,
        "features": feature_reports,
    }

    return processed, report


def preprocess_abnormal_segment_features(
    df: pd.DataFrame,
    feature_columns: list[str],
) -> tuple[pd.DataFrame, dict[str, Any]]:
    filtered, quality_report = _apply_abnormal_segment_quality_filter(
        df,
        feature_columns,
    )
    processed, outlier_report = _winsorize_and_impute_abnormal_features(
        filtered,
        feature_columns,
    )

    return processed, {
        "target": "abnormal_segment_features",
        "quality_filter": quality_report,
        "outlier_treatment": outlier_report,
    }
