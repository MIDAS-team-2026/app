import numpy as np
import pandas as pd


# 유사도 계산에 사용할 음향 특징 컬럼 목록입니다.
# 전체 음성 기반 모델이므로 audio_duration도 포함합니다.
FEATURE_COLUMNS = [
    "audio_duration",
    "segment_count",
    "rms_mean",
    "rms_std",
    "zcr_mean",
    "zcr_std",
    "spectral_centroid_mean",
    "spectral_centroid_std",
]

PAUSE_FEATURE_COLUMNS = [
    "pause_count",
    "total_pause_duration",
    "avg_pause_duration",
    "max_pause_duration",
    "pause_ratio",
    "response_latency",
]

EGEMAPS_FEATURE_COLUMNS = [
    "f0_semitone_mean",
    "f0_semitone_stddev_norm",
    "jitter_local",
    "shimmer_local_db",
    "hnr_db",
    "voiced_segments_per_sec",
    "mean_voiced_segment_length",
    "mean_unvoiced_segment_length",
    "voice_break_count",
    "voice_break_ratio",
]

FEATURE_COLUMNS += PAUSE_FEATURE_COLUMNS
FEATURE_COLUMNS += EGEMAPS_FEATURE_COLUMNS
FEATURE_COLUMNS += [f"mfcc_{i}_mean" for i in range(1, 14)]
FEATURE_COLUMNS += [f"mfcc_{i}_std" for i in range(1, 14)]


def _available_reference_columns(reference_df):
    columns = [column for column in FEATURE_COLUMNS if column in reference_df.columns]
    numeric_df = reference_df[columns].apply(pd.to_numeric, errors="coerce") if columns else pd.DataFrame()
    columns = [column for column in columns if numeric_df[column].notna().any()]

    if not columns:
        raise ValueError("reference feature CSV에 사용할 수 있는 음향 특징 컬럼이 없습니다.")

    return columns


def _available_similarity_columns(sample_features, reference_mean, reference_std):
    sample_series = pd.Series(sample_features)
    mean_series = pd.Series(reference_mean)
    std_series = pd.Series(reference_std)

    columns = []
    for column in FEATURE_COLUMNS:
        if column not in sample_series.index or column not in mean_series.index or column not in std_series.index:
            continue

        sample_value = pd.to_numeric(pd.Series([sample_series[column]]), errors="coerce").iloc[0]
        mean_value = pd.to_numeric(pd.Series([mean_series[column]]), errors="coerce").iloc[0]
        std_value = pd.to_numeric(pd.Series([std_series[column]]), errors="coerce").iloc[0]

        if pd.notna(sample_value) and pd.notna(mean_value) and pd.notna(std_value):
            columns.append(column)

    if not columns:
        raise ValueError("사용자 음성과 reference profile 사이에 공통 음향 특징 컬럼이 없습니다.")

    return columns


def build_reference_profile(reference_df):
    """
    발화 이상 참고군의 평균 벡터와 표준편차 벡터를 생성하는 함수입니다.

    현재 프로젝트에서는 25.언어+뇌신경장애 데이터를 발화 이상 참고군으로 사용합니다.
    각 음성 파일에서 추출한 음향 특징들의 평균과 표준편차를 계산하여,
    새로운 사용자 음성이 이 참고군과 얼마나 유사한지 비교할 때 사용합니다.
    """

    columns = _available_reference_columns(reference_df)
    numeric_df = reference_df[columns].apply(pd.to_numeric, errors="coerce")
    reference_mean = numeric_df.mean()
    reference_std = numeric_df.std().replace(0, 1).fillna(1)

    return reference_mean, reference_std


def calculate_dysarthria_similarity(sample_features, reference_mean, reference_std):
    """
    사용자 음성과 발화 이상 참고군 사이의 음향적 유사도를 계산하는 함수입니다.

    정상 대조군 데이터가 확보되지 않았기 때문에,
    본 prototype에서는 정상/구음장애 분류 확률을 계산하지 않습니다.
    대신 사용자 음성 특징이 25.언어+뇌신경장애 참고군의 평균 특징과
    얼마나 가까운지를 거리 기반으로 계산합니다.
    """

    columns = _available_similarity_columns(sample_features, reference_mean, reference_std)

    sample_vector = pd.Series(sample_features).reindex(columns).astype(float)
    reference_mean = pd.Series(reference_mean).reindex(columns).astype(float)
    reference_std = pd.Series(reference_std).reindex(columns).replace(0, 1).fillna(1).astype(float)

    # 참고군의 평균과 표준편차를 기준으로 z-score 차이를 계산합니다.
    z_diff = (sample_vector - reference_mean) / reference_std

    # 여러 음향 특징의 차이를 하나의 거리값으로 요약합니다.
    distance = np.sqrt(np.mean(z_diff ** 2))

    # 거리가 가까울수록 유사도가 높아지도록 0~1 사이의 값으로 변환합니다.
    similarity = 1 / (1 + distance)

    return {
        "dysarthria_similarity_score": round(float(similarity), 3),
        "distance_from_reference": round(float(distance), 3),
        "reference_feature_count": len(columns),
        "reference_feature_columns": columns,
    }


def calculate_dynamic_thresholds(similarity_scores):
    """
    참고군 내부 테스트 결과의 유사도 분포를 바탕으로
    Low / Medium / High 기준값을 계산하는 함수입니다.

    고정 기준값을 사용하면 데이터 분포에 맞지 않을 수 있으므로,
    현재 prototype에서는 1사분위수와 3사분위수를 기준으로 사용합니다.
    """

    low_threshold = similarity_scores.quantile(0.25)
    high_threshold = similarity_scores.quantile(0.75)

    return float(low_threshold), float(high_threshold)


def convert_similarity_to_reference_score(similarity, low_threshold, high_threshold):
    """
    유사도 점수를 참고군 유사도 수준과 점수로 변환하는 함수입니다.

    주의:
    이 값은 정상/비정상 진단 결과가 아닙니다.
    25.언어+뇌신경장애 참고군과의 음향적 유사도 수준을 의미합니다.
    """

    if similarity >= high_threshold:
        return {
            "reference_similarity_level": "High",
            "reference_similarity_score": 15,
        }
    elif similarity >= low_threshold:
        return {
            "reference_similarity_level": "Medium",
            "reference_similarity_score": 7,
        }
    else:
        return {
            "reference_similarity_level": "Low",
            "reference_similarity_score": 0,
            
        }