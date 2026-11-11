"""
feature_preprocessor.py
========================
train_dysarthria_rf.py(학습)와 user_turn_analysis.py(실서비스 추론)가
동일한 FeaturePreprocessor 클래스를 공유하기 위한 모듈.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler


class FeaturePreprocessor:
    """결측치 대체 -> (편포 피처) log1p 변환 -> 표준화 를 수행하는 전처리기."""

    def __init__(self, skew_threshold: float = 0.75):
        self.skew_threshold = skew_threshold
        self.imputer: SimpleImputer | None = None
        self.scaler: StandardScaler | None = None
        self.log_cols: list[str] = []
        self.shift_values: dict[str, float] = {}
        self.feature_names_: list[str] = []

    def fit(self, X: pd.DataFrame) -> pd.DataFrame:
        self.feature_names_ = list(X.columns)
        self.imputer = SimpleImputer(strategy="median")
        X_imputed = pd.DataFrame(
            self.imputer.fit_transform(X), columns=self.feature_names_, index=X.index
        )

        skews = X_imputed.skew()
        acoustic_log_keywords = ["f0", "hz", "energy", "amplitude", "loudness"]
        domain_log_cols = [
            c for c in self.feature_names_ if any(k in c.lower() for k in acoustic_log_keywords)
        ]
        skew_log_cols = [c for c in self.feature_names_ if abs(skews[c]) > self.skew_threshold]

        self.log_cols = list(set(skew_log_cols + domain_log_cols))

        X_logged = X_imputed.copy()
        for c in self.log_cols:
            shift = max(0.0, -X_imputed[c].min()) + 1e-6
            self.shift_values[c] = shift
            X_logged[c] = np.log1p(X_imputed[c] + shift)

        self.scaler = StandardScaler()
        X_scaled = pd.DataFrame(
            self.scaler.fit_transform(X_logged), columns=self.feature_names_, index=X.index
        )
        return X_scaled

    def transform(self, X: pd.DataFrame) -> pd.DataFrame:
        X = X[self.feature_names_]
        X_imputed = pd.DataFrame(
            self.imputer.transform(X), columns=self.feature_names_, index=X.index
        )
        X_logged = X_imputed.copy()
        for c in self.log_cols:
            X_logged[c] = np.log1p(X_imputed[c] + self.shift_values[c])
        X_scaled = pd.DataFrame(
            self.scaler.transform(X_logged), columns=self.feature_names_, index=X.index
        )
        return X_scaled


def predict_from_feature_dict(feature_dict: dict, model, preprocessor: "FeaturePreprocessor") -> dict:
    """
    추출된 피처 딕셔너리를 받아 RF 모델로부터 비정상 확률을 예측한다.
    preprocessor.feature_names_ 에 없는 키는 무시되고, 있어야 하는데 feature_dict에
    없는 컬럼은 NaN으로 채워져 학습 시 저장된 median으로 대체(impute)된다.
    """
    row = pd.DataFrame([feature_dict])
    missing = [c for c in preprocessor.feature_names_ if c not in row.columns]
    for c in missing:
        row[c] = np.nan
    X_processed = preprocessor.transform(row)
    proba_abnormal = model.predict_proba(X_processed)[0, 1]
    pred_label = int(proba_abnormal >= 0.5)
    return {
        "predicted_label": pred_label,
        "abnormal_probability": float(proba_abnormal),
    }