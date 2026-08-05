"""
train_dysarthria_rf.py
========================
정상군(노인 챗봇 대화 음성) vs 비정상군(구음장애/신경학적 언어장애 음성, 8초 단위 분할)을
음향 특징으로 구분하는 Random Forest 분류 모델 학습 파이프라인.
"""

import json
import warnings
import os
from pathlib import Path
from typing import Tuple, List

# Windows 스레드/병렬처리 경고 완벽 차단
os.environ["PYTHONWARNINGS"] = "ignore"
warnings.filterwarnings("ignore")
warnings.filterwarnings("ignore", category=UserWarning)

import joblib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.inspection import permutation_importance
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    RocCurveDisplay,
    classification_report,
    confusion_matrix,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, GroupShuffleSplit, StratifiedKFold
from sklearn.preprocessing import StandardScaler

# ============================================================================
# 0. 설정값 (CONFIG)
# ============================================================================

AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = Path(os.getenv("MIDAS_EXTRACTED_DIR", AI_ANALYSIS_ROOT / "outputs"))

NORMAL_CSV = Path(
    os.getenv("MIDAS_NORMAL_CSV", OUTPUT_DIR / "elderly_chatbot_reference_features_egemaps_sample_1000.csv")
)
ABNORMAL_CSV = Path(
    os.getenv("MIDAS_ABNORMAL_CSV", OUTPUT_DIR / "dysarthria_neuro_25_reference_features_egemaps_full.partial.csv")
)

OUTPUT_DIR = Path("./analysis/artifacts")
RANDOM_STATE = 42
TEST_SIZE = 0.2

# 피처 개수 대폭 확대 (MFCC 포함 시 약 35~40개 가용)
TOP_N_FEATURES = 40
SKEW_THRESHOLD = 0.75

N_ESTIMATORS_GRID = [200, 400, 600]
MAX_DEPTH_GRID = [None, 6, 10, 16]
MIN_SAMPLES_LEAF_GRID = [1, 2, 4]

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def get_class_balanced_weights(y, beta=0.99):
    classes = np.unique(y)
    counts = np.array([np.sum(y == c) for c in classes])

    effective_num = 1.0 - np.power(beta, counts)
    weights = (1.0 - beta) / effective_num

    weights = weights / np.sum(weights) * len(classes)
    return dict(zip(classes, weights))


# ============================================================================
# 1. 데이터 로드 & 라벨링 (Speaker Group 분할 적용)
# ============================================================================

# 교체 전) 적용할 파라미터
METADATA_EXCLUDE = {
    "json_path", "folder_name", "json_file_name", "audio_file_name", "audio_path",
    "file_size", "disease_type", "subcategory1", "subcategory2", "subcategory3",
    "subcategory6", "language", "sampling_rate", "recording_environment",
    "recording_device", "file_format", "sex", "gender", "age", "area", "test_method",
    "reference_group", "feature_extract_range", "_source_row_index",
    "source_audio_duration", "egemaps_available", "egemaps_error",
    "egemaps_feature_count", "feature_schema_version", "extracted_at", "matched",
    "script_id", "transcript", "record_time", "record_quality", "record_date",
    "script_set_no", "record_environment", "collection_unit_code", "city_code",
    "record_unit", "conversation_theme", "recorder_id", "char_count", "word_count",
    "unique_word_count", "lexical_diversity", "repetition_ratio", "avg_word_length",
    "short_answer_flag", "record_time_float", "speech_rate_word", "speech_rate_char",
    "slow_speech_flag", "long_recording_flag", "low_content_slow_speech_flag",
    "sample_seed", "sample_group", "play_time", "segment_count",
}

"""
# 교체 후) 적용할 파라미터
METADATA_EXCLUDE = {
    "json_path", "folder_name", "json_file_name", "audio_file_name", "audio_path",
    "file_size", "disease_type", "subcategory1", "subcategory2", "subcategory3",
    "subcategory6", "language", "sampling_rate", "recording_environment",
    "recording_device", "file_format", "sex", "gender", "age", "area", "test_method",
    "reference_group", "feature_extract_range", "_source_row_index",
    "source_audio_duration", "egemaps_available", "egemaps_error",
    "egemaps_feature_count", "feature_schema_version", "extracted_at", "matched",
    "script_id", "transcript", "record_time", "record_quality", "record_date",
    "script_set_no", "record_environment", "collection_unit_code", "city_code",
    "record_unit", "conversation_theme", "recorder_id", "char_count", "word_count",
    "unique_word_count", "lexical_diversity", "repetition_ratio", "avg_word_length",
    "short_answer_flag", "record_time_float", "speech_rate_word", "speech_rate_char",
    "slow_speech_flag", "long_recording_flag", "low_content_slow_speech_flag",
    "sample_seed", "sample_group", "play_time", "segment_count",
    # 청킹본 메타데이터 추가
    "segment_id", "source_dataset", "label", "label_name", "source_audio_file_name",
    "source_audio_path", "source_json_path", "source_row_index", "speaker_key",
    "segment_index", "segment_start_sec", "segment_end_sec"
}
"""

# 제외할 파라미터
DURATION_CONFOUNDED = {
    "audio_duration", "segment_duration", "pause_count", "total_pause_duration",
    "response_latency", "speech_duration", "max_pause_duration", "avg_pause_duration",
    "voice_break_count",
}

# 교체 전) 환경/마이크 볼륨 등에 민감한 절대 에너지값 배제
MIC_ENVIRONMENT_CONFOUNDED = {
    "spectral_centroid_mean", "spectral_centroid_std", "zcr_mean",
    "spectral_flux_mean", "spectral_flux_std", "spectral_slope_mean", "spectral_slope_std"
}

"""
# 교체 후) 환경/마이크 볼륨 등에 민감한 절대 에너지값 배제
MIC_ENVIRONMENT_CONFOUNDED = {
    "spectral_centroid_mean", "spectral_centroid_std", "zcr_mean", "zcr_std",
    "spectral_flux_mean", "spectral_flux_std", "spectral_slope_mean", "spectral_slope_std",
    "rms_mean", "rms_std"
}
"""

def load_labeled_dataset(normal_csv: str, abnormal_csv: str) -> Tuple[pd.DataFrame, List[str], str]:
    df_normal = pd.read_csv(normal_csv)
    df_abnormal = pd.read_csv(abnormal_csv)

    df_normal = df_normal.copy()
    df_abnormal = df_abnormal.copy()

    common_cols = set(df_normal.columns) & set(df_abnormal.columns)

    # MFCC 제외 조건 삭제 (파라미터 개수 확보를 위함)
    feature_cols = sorted(
        c for c in common_cols
        if c not in METADATA_EXCLUDE
        and c not in DURATION_CONFOUNDED
        and c not in MIC_ENVIRONMENT_CONFOUNDED
    )

    # 정상군 화자 그룹화
    if "recorder_id" in df_normal.columns and df_normal["recorder_id"].nunique() > 1:
        df_normal["speaker_group"] = "normal_" + df_normal["recorder_id"].astype(str)
    else:
        df_normal["speaker_group"] = "normal_" + df_normal["audio_file_name"].astype(str)

    # 비정상군 화자 그룹화 (새로운 스키마의 speaker_key 적극 활용)
    if "speaker_key" in df_abnormal.columns:
        df_abnormal["speaker_group"] = "abnormal_" + df_abnormal["speaker_key"].astype(str)
    elif "source_audio_file_name" in df_abnormal.columns:
        df_abnormal["speaker_group"] = "abnormal_" + df_abnormal["source_audio_file_name"].astype(str)
    else:
        df_abnormal["speaker_group"] = "abnormal_" + df_abnormal.index.astype(str)

    df_normal["label"] = 0
    df_abnormal["label"] = 1

    group_col = "speaker_group"
    keep_cols = feature_cols + ["label", group_col]

    combined = pd.concat(
        [df_normal[keep_cols], df_abnormal[keep_cols]],
        axis=0, ignore_index=True,
    )

    print(f"[load] 정상군 n={len(df_normal)}, 비정상군 n={len(df_abnormal)}")
    print(f"[load] 공통 피처 {len(feature_cols)}개 선정 완료")
    print(f"[load] 화자 그룹 분할 기준: '{group_col}' (정상군 화자: {df_normal['speaker_group'].nunique()}명, 비정상군 화자: {df_abnormal['speaker_group'].nunique()}명)")

    return combined, feature_cols, group_col


# ============================================================================
# 2. 전처리 (가중치 로직 제거 및 단순화)
# ============================================================================

class FeaturePreprocessor:
    def __init__(self, skew_threshold=SKEW_THRESHOLD):
        self.skew_threshold = skew_threshold
        self.imputer = None
        self.scaler = None
        self.log_cols = []
        self.shift_values = {}
        self.feature_names_ = []

    def fit(self, X: pd.DataFrame):
        self.feature_names_ = list(X.columns)
        self.imputer = SimpleImputer(strategy="median")
        X_imputed = pd.DataFrame(self.imputer.fit_transform(X), columns=self.feature_names_, index=X.index)

        skews = X_imputed.skew()
        acoustic_log_keywords = ['f0', 'hz', 'energy', 'amplitude', 'loudness']
        domain_log_cols = [c for c in self.feature_names_ if any(k in c.lower() for k in acoustic_log_keywords)]
        skew_log_cols = [c for c in self.feature_names_ if abs(skews[c]) > self.skew_threshold]

        self.log_cols = list(set(skew_log_cols + domain_log_cols))
        print(f"[preprocess] log1p 변환 적용 피처 {len(self.log_cols)}개")

        X_logged = X_imputed.copy()
        for c in self.log_cols:
            shift = max(0.0, -X_imputed[c].min()) + 1e-6
            self.shift_values[c] = shift
            X_logged[c] = np.log1p(X_imputed[c] + shift)

        self.scaler = StandardScaler()
        X_scaled = pd.DataFrame(self.scaler.fit_transform(X_logged), columns=self.feature_names_, index=X.index)
        return X_scaled

    def transform(self, X: pd.DataFrame):
        X = X[self.feature_names_]
        X_imputed = pd.DataFrame(self.imputer.transform(X), columns=self.feature_names_, index=X.index)
        X_logged = X_imputed.copy()
        for c in self.log_cols:
            X_logged[c] = np.log1p(X_imputed[c] + self.shift_values[c])
        X_scaled = pd.DataFrame(self.scaler.transform(X_logged), columns=self.feature_names_, index=X.index)
        return X_scaled


# ============================================================================
# 3. 1차 RF로 변수 중요도 산출
# ============================================================================

def select_top_features(X_train, y_train, X_val, y_val, top_n=TOP_N_FEATURES):
    cb_weights = get_class_balanced_weights(y_train, beta=0.99)

    rf_probe = RandomForestClassifier(
        n_estimators=300,
        random_state=RANDOM_STATE,
        class_weight=cb_weights,
        n_jobs=-1,
    )
    rf_probe.fit(X_train, y_train)
    impurity_importance = pd.Series(rf_probe.feature_importances_, index=X_train.columns)

    perm_result = permutation_importance(
        rf_probe, X_val, y_val, n_repeats=20, random_state=RANDOM_STATE, n_jobs=1
    )
    perm_importance = pd.Series(perm_result.importances_mean, index=X_train.columns)

    def normalize(s):
        rng = s.max() - s.min()
        return (s - s.min()) / rng if rng > 0 else s * 0

    combined_rank = (normalize(impurity_importance) + normalize(perm_importance)) / 2
    ranked = combined_rank.sort_values(ascending=False)

    importance_table = pd.DataFrame({
        "impurity_importance": impurity_importance,
        "permutation_importance": perm_importance,
        "combined_score": combined_rank,
    }).sort_values("combined_score", ascending=False)

    # 전체 피처 수가 top_n보다 적을 경우 방어 코드 추가
    actual_top_n = min(top_n, len(ranked))
    top_features = ranked.head(actual_top_n).index.tolist()

    print(f"\n[feature selection] 상위 {actual_top_n}개 변수 추출 완료 (요청: {top_n}개)")
    return top_features, importance_table


# ============================================================================
# 4. 최종 모델 학습
# ============================================================================

def train_final_model(X_train, y_train):
    cb_weights = get_class_balanced_weights(y_train, beta=0.99)

    rf = RandomForestClassifier(
        random_state=RANDOM_STATE,
        class_weight=cb_weights,
    )
    param_grid = {
        "n_estimators": N_ESTIMATORS_GRID,
        "max_depth": MAX_DEPTH_GRID,
        "min_samples_leaf": MIN_SAMPLES_LEAF_GRID,
    }
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=RANDOM_STATE)

    grid = GridSearchCV(rf, param_grid, scoring="roc_auc", cv=cv, n_jobs=1)
    grid.fit(X_train, y_train)

    print(f"\n[final model] best params: {grid.best_params_}")
    print(f"[final model] best CV ROC-AUC: {grid.best_score_:.4f}")
    return grid.best_estimator_


# ============================================================================
# 5. 평가 & 6. 메인
# ============================================================================

def evaluate_model(model, X_test, y_test, feature_names, output_dir: Path):
    y_pred = model.predict(X_test)
    y_proba = model.predict_proba(X_test)[:, 1]

    print("\n[evaluation] classification report:")
    print(classification_report(y_test, y_pred, target_names=["정상군", "비정상군"]))

    auc = roc_auc_score(y_test, y_proba)
    print(f"[evaluation] ROC-AUC: {auc:.4f}")

    cm = confusion_matrix(y_test, y_pred)
    fig, ax = plt.subplots(figsize=(5, 5))
    ConfusionMatrixDisplay(cm, display_labels=["정상군", "비정상군"]).plot(ax=ax, cmap="Blues")
    plt.tight_layout()
    plt.savefig(output_dir / "confusion_matrix.png", dpi=150)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(5, 5))
    RocCurveDisplay.from_predictions(y_test, y_proba, ax=ax)
    plt.tight_layout()
    plt.savefig(output_dir / "roc_curve.png", dpi=150)
    plt.close(fig)

    importances = pd.Series(model.feature_importances_, index=feature_names).sort_values()
    fig, ax = plt.subplots(figsize=(8, max(4, 0.35 * len(feature_names))))
    importances.plot(kind="barh", ax=ax)
    ax.set_title("Final Random Forest Feature Importance")
    plt.tight_layout()
    plt.savefig(output_dir / "final_feature_importance.png", dpi=150)
    plt.close(fig)

    return {"roc_auc": float(auc), "confusion_matrix": cm.tolist()}

def predict_from_feature_dict(feature_dict: dict, model, preprocessor: FeaturePreprocessor):
    row = pd.DataFrame([feature_dict])
    missing = [c for c in preprocessor.feature_names_ if c not in row.columns]
    for c in missing:
        row[c] = np.nan
    X_processed = preprocessor.transform(row)
    proba_abnormal = model.predict_proba(X_processed)[0, 1]
    return {
        "predicted_label": int(proba_abnormal >= 0.5),
        "abnormal_probability": float(proba_abnormal),
    }

def main():
    combined, candidate_features, group_col = load_labeled_dataset(NORMAL_CSV, ABNORMAL_CSV)

    X = combined[candidate_features]
    y = combined["label"]
    groups = combined[group_col]

    gss_test = GroupShuffleSplit(n_splits=1, test_size=TEST_SIZE, random_state=RANDOM_STATE)
    train_idx, test_idx = next(gss_test.split(X, y, groups))

    X_train_raw, X_test_raw = X.iloc[train_idx], X.iloc[test_idx]
    y_train, y_test = y.iloc[train_idx], y.iloc[test_idx]
    groups_train = groups.iloc[train_idx]

    gss_val = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=RANDOM_STATE)
    fit_idx, val_idx = next(gss_val.split(X_train_raw, y_train, groups_train))

    X_fit_raw, X_val_raw = X_train_raw.iloc[fit_idx], X_train_raw.iloc[val_idx]
    y_fit, y_val = y_train.iloc[fit_idx], y_train.iloc[val_idx]

    print(f"\n[GroupSplit] Train 샘플 수: {len(X_train_raw)} (Fit: {len(X_fit_raw)}, Val: {len(X_val_raw)})")
    print(f"[GroupSplit] Test 샘플 수: {len(X_test_raw)}")

    preprocessor_probe = FeaturePreprocessor(skew_threshold=SKEW_THRESHOLD)
    X_fit = preprocessor_probe.fit(X_fit_raw)
    X_val = preprocessor_probe.transform(X_val_raw)

    top_features, importance_table = select_top_features(X_fit, y_fit, X_val, y_val, TOP_N_FEATURES)
    importance_table.to_csv(OUTPUT_DIR / "feature_importance_full.csv")

    preprocessor_final = FeaturePreprocessor(skew_threshold=SKEW_THRESHOLD)
    X_train_final = preprocessor_final.fit(X_train_raw[top_features])
    X_test_final = preprocessor_final.transform(X_test_raw[top_features])

    model = train_final_model(X_train_final, y_train)
    metrics = evaluate_model(model, X_test_final, y_test, top_features, OUTPUT_DIR)

    joblib.dump(model, OUTPUT_DIR / "random_forest_model.joblib")
    joblib.dump(preprocessor_final, OUTPUT_DIR / "preprocessor.joblib")

    with open(OUTPUT_DIR / "selected_features.json", "w", encoding="utf-8") as f:
        json.dump(top_features, f, ensure_ascii=False, indent=2)

    print(f"\n[done] 아티팩트 저장 완료: {OUTPUT_DIR.resolve()}")

    example = X_test_raw.iloc[0][top_features].to_dict()
    example_result = predict_from_feature_dict(example, model, preprocessor_final)
    print(f"\n[example inference] {example_result}")


if __name__ == "__main__":
    main()