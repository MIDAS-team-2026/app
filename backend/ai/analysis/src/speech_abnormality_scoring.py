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

FEATURE_COLUMNS += [f"mfcc_{i}_mean" for i in range(1, 14)]
FEATURE_COLUMNS += [f"mfcc_{i}_std" for i in range(1, 14)]


def build_reference_profile(reference_df):
    """
    발화 이상 참고군의 평균 벡터와 표준편차 벡터를 생성하는 함수입니다.

    현재 프로젝트에서는 25.언어+뇌신경장애 데이터를 발화 이상 참고군으로 사용합니다.
    각 음성 파일에서 추출한 음향 특징들의 평균과 표준편차를 계산하여,
    새로운 사용자 음성이 이 참고군과 얼마나 유사한지 비교할 때 사용합니다.
    """

    reference_mean = reference_df[FEATURE_COLUMNS].mean()
    reference_std = reference_df[FEATURE_COLUMNS].std().replace(0, 1)

    return reference_mean, reference_std


def calculate_dysarthria_similarity(sample_features, reference_mean, reference_std):
    """
    사용자 음성과 발화 이상 참고군 사이의 음향적 유사도를 계산하는 함수입니다.

    정상 대조군 데이터가 확보되지 않았기 때문에,
    본 prototype에서는 정상/구음장애 분류 확률을 계산하지 않습니다.
    대신 사용자 음성 특징이 25.언어+뇌신경장애 참고군의 평균 특징과
    얼마나 가까운지를 거리 기반으로 계산합니다.
    """

    sample_vector = pd.Series(sample_features)[FEATURE_COLUMNS]

    # 참고군의 평균과 표준편차를 기준으로 z-score 차이를 계산합니다.
    z_diff = (sample_vector - reference_mean) / reference_std

    # 여러 음향 특징의 차이를 하나의 거리값으로 요약합니다.
    distance = np.sqrt(np.mean(z_diff ** 2))

    # 거리가 가까울수록 유사도가 높아지도록 0~1 사이의 값으로 변환합니다.
    similarity = 1 / (1 + distance)

    return {
        "dysarthria_similarity_score": round(float(similarity), 3),
        "distance_from_reference": round(float(distance), 3),
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