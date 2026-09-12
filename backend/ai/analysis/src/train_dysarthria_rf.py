"""
train_dysarthria_rf.py
========================
정상군(노인 챗봇 대화 음성) vs 비정상군(구음장애/신경학적 언어장애 음성, 8초 단위 분할)을
음향 특징으로 구분하는 Random Forest 분류 모델 학습 파이프라인.
"""

import json
import sys
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
from sklearn.calibration import CalibratedClassifierCV

# user_turn_analysis.py와 동일한 클래스를 공유
sys.path.append(str(Path(__file__).resolve().parent))
from feature_preprocessor import FeaturePreprocessor, predict_from_feature_dict  # noqa: E402

# ============================================================================
# 0. 설정값 (CONFIG)
# ============================================================================

AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.getenv("MIDAS_EXTRACTED_DIR", AI_ANALYSIS_ROOT / "outputs"))
ARTIFACT_DIR = Path(os.getenv("MIDAS_RF_ARTIFACT_DIR", AI_ANALYSIS_ROOT / "artifacts"))

NORMAL_CSV = Path(
    os.getenv("MIDAS_NORMAL_CSV", DATA_DIR / "elderly_chatbot_reference_features_egemaps_sample_1000.csv")
)

ABNORMAL_CSV = Path(
    os.getenv("MIDAS_ABNORMAL_CSV", DATA_DIR / "dysarthria_neuro_25_segment_features_egemaps.csv")
)

RANDOM_STATE = 42
TEST_SIZE = 0.2

# 피처 개수: 논문 기준에 맞춰 재조정
# - Kim et al. (HCAI@CIKM'25, XAI-dysarthria): 12개 DDK 특징(+성별)만으로 severity 분류,
#   그중 SLP 검증에 사용한 핵심 특징은 6개로 압축 → "소수의 해석 가능한 피처"가 임상 타당성 확보에 유리.
# - Xue et al. (SLaTE 2019, eGeMAPS): stepwise 회귀로 최종 채택된 피처 수는 대부분 1~9개,
#   가장 많은 경우도 14개(DIA subset C, combined speakers)에 불과.
# - Eyben et al. (GeMAPS, TAFFC 2015): "minimalistic" 62파라미터 집합이 6000여개의
#   brute-force 집합과 대등한 성능을 보이며, 과적합/일반화 측면에서 오히려 더 유리함을 실증.
# 위 세 논문 모두 "적은 수의, 해석 가능한 음향 피처"를 지향하므로, 본 파이프라인에서도
# 사용 가능한 피처 풀(아래 필터링 후 약 19개, MFCC 1~4만 사용)에서 상위 15개 내외로 좁힌다.
TOP_N_FEATURES = 15
SKEW_THRESHOLD = 0.75

N_ESTIMATORS_GRID = [200, 400, 600]
MAX_DEPTH_GRID = [None, 6, 10, 16]
MIN_SAMPLES_LEAF_GRID = [1, 2, 4]

ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)


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

# 메타데이터 배제 파라미터
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
    # 세그먼트/청킹본 메타데이터 -- 현재 비정상군 전용 식별자.
    # 정상군 CSV에는 아직 없어 common_cols 교집합에서 자연히 제외되지만, 이름이 겹치는
    # 사고를 막기 위해 명시적으로 등록해 둔다.
    "segment_id", "source_dataset", "label", "label_name", "source_audio_file_name",
    "source_audio_path", "source_json_path", "source_row_index", "speaker_key",
    "segment_index", "segment_start_sec", "segment_end_sec", "segment_duration",
    # 정상군 CSV 전용(신규 스키마) 추가 메타데이터
    "source_record_time_float",
}

# 제외할 파라미터: 녹음 길이/침묵 구간과 직접 연동되어 분류에 "누수(leakage)"를 일으킬 수 있는 값
DURATION_CONFOUNDED = {
    "audio_duration", "segment_duration", "pause_count", "total_pause_duration",
    "response_latency", "speech_duration", "max_pause_duration", "avg_pause_duration",
    "voice_break_count",
}

# 환경/마이크 볼륨 등 절대 에너지값에 민감한 피처 배제
# Eyben et al. (GeMAPS, TAFFC 2015) -> loudness(상대/청각 가중 에너지)는 채택하되
# 정규화되지 않은 절대 RMS 에너지는 채택하지 않음 -- 녹음 기기/거리/볼륨 차이에 취약하기 때문
MIC_ENVIRONMENT_CONFOUNDED = {
    "spectral_centroid_mean", "spectral_centroid_std", "zcr_mean", "zcr_std",
    "spectral_flux_mean", "spectral_flux_std", "spectral_slope_mean", "spectral_slope_std",
    "rms_mean", "rms_std",
}

# 고차 MFCC 배제 (파라미터 선정 기준을 논문 수준에 맞춤)
# Eyben et al. (GeMAPS, TAFFC 2015) -> eGeMAPS 확장 세트도 MFCC 1~4만 채택
# (필요 시 아래 목록에서 원하는 항목만 해제하여 다시 후보 피처 풀에 포함)
MFCC_HIGH_ORDER_EXCLUDE = {
    "mfcc_5_mean", "mfcc_5_std",
    "mfcc_6_mean", "mfcc_6_std",
    "mfcc_7_mean", "mfcc_7_std",
    "mfcc_8_mean", "mfcc_8_std",
    "mfcc_9_mean", "mfcc_9_std",
    "mfcc_10_mean", "mfcc_10_std",
    "mfcc_11_mean", "mfcc_11_std",
    "mfcc_12_mean", "mfcc_12_std",
    "mfcc_13_mean", "mfcc_13_std",
}

def load_labeled_dataset(normal_csv: str, abnormal_csv: str) -> Tuple[pd.DataFrame, List[str], str]:
    df_normal = pd.read_csv(normal_csv)
    df_abnormal = pd.read_csv(abnormal_csv)

    df_normal = df_normal.copy()
    df_abnormal = df_abnormal.copy()

    common_cols = set(df_normal.columns) & set(df_abnormal.columns)

    # 논문 기준 파라미터 선정: 메타데이터 / 길이-누수 / 마이크-환경 민감 피처 / 고차 MFCC 배제
    feature_cols = sorted(
        c for c in common_cols
        if c not in METADATA_EXCLUDE
        and c not in DURATION_CONFOUNDED
        and c not in MIC_ENVIRONMENT_CONFOUNDED
        and c not in MFCC_HIGH_ORDER_EXCLUDE
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

# FeaturePreprocessor는 feature_preprocessor.py(공용 모듈)에서 import해서 사용한다.
# (user_turn_analysis.py의 추론 코드와 클래스를 공유하기 위함)


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

    # 건강 점수 분포 히스토그램 저장 로직
    # 확률값을 100점 만점 건강 점수로 변환 (정상=100, 비정상=0)
    health_scores = (1.0 - y_proba) * 100.0

    # 정상군(label=0)과 비정상군(label=1)의 점수 분리
    scores_normal = health_scores[y_test == 0]
    scores_abnormal = health_scores[y_test == 1]

    fig, ax = plt.subplots(figsize=(8, 6))

    # 히스토그램 그리기 (투명도 alpha=0.6을 주어 겹치는 부분 확인 가능)
    ax.hist(scores_normal, bins=20, alpha=0.6, color='blue', label='Normal (정상군)', edgecolor='black')
    ax.hist(scores_abnormal, bins=20, alpha=0.6, color='red', label='Abnormal (비정상군)', edgecolor='black')

    ax.set_title("Health Score Distribution by Class")
    ax.set_xlabel("Health Score (100 = Normal, 0 = Abnormal)")
    ax.set_ylabel("Count (Number of samples)")
    ax.legend(loc='upper center')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()

    # 출력 디렉터리에 health_score_histogram.png 이름으로 저장
    plt.savefig(output_dir / "health_score_histogram.png", dpi=150)
    plt.close(fig)
    print(f"[evaluation] 건강 점수 히스토그램 저장 완료: {output_dir / 'health_score_histogram.png'}")
    # ---------------------------------------------------------

    return {"roc_auc": float(auc), "confusion_matrix": cm.tolist()}

# predict_from_feature_dict()도 feature_preprocessor.py(공용 모듈)에서 import해서 사용한다.

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
    importance_table.to_csv(ARTIFACT_DIR / "feature_importance_full.csv")

    preprocessor_final = FeaturePreprocessor(skew_threshold=SKEW_THRESHOLD)
    X_train_final = preprocessor_final.fit(X_train_raw[top_features])
    X_test_final = preprocessor_final.transform(X_test_raw[top_features])

    model = train_final_model(X_train_final, y_train)
    metrics = evaluate_model(model, X_test_final, y_test, top_features, ARTIFACT_DIR)

    # user_turn_analysis.py의 RF_MODEL_PATH / PREPROCESSOR_PATH가 바라보는 파일명과
    # 반드시 동일해야 한다 (random_forest_model.joblib / preprocessor.joblib).
    joblib.dump(model, ARTIFACT_DIR / "random_forest_model.joblib")
    joblib.dump(preprocessor_final, ARTIFACT_DIR / "preprocessor.joblib")

    with open(ARTIFACT_DIR / "selected_features.json", "w", encoding="utf-8") as f:
        json.dump(top_features, f, ensure_ascii=False, indent=2)

    training_report = {
        "normal_csv": str(NORMAL_CSV),
        "abnormal_csv": str(ABNORMAL_CSV),
        "selected_features": top_features,
        "metrics": metrics,
    }

    with open(ARTIFACT_DIR / "random_forest_training_report.json", "w", encoding="utf-8") as f:
        json.dump(training_report, f, ensure_ascii=False, indent=2)

    print(f"\n[done] 모델 아티팩트 및 리포트 저장 완료: {ARTIFACT_DIR.resolve()}")

    example = X_test_raw.iloc[0][top_features].to_dict()

    example_df = pd.DataFrame([example])

    # # 이게 원래 점수. 분포 변환한게 아래임. 너무 정확도 낮으면 아래 지우고 그냥 이거 사용 기
    # prob_abnormal = model.predict_proba(example_df)[0, 1]
    #
    # # 100점 만점으로 변환 (비정상일 확률이 1이면 0점, 0이면 100점)
    # health_score = (1.0 - prob_abnormal) * 100.0

    # 여기서부터 ----------------------------------
    X_val_final = preprocessor_final.transform(X_val_raw[top_features])

    calibrated_model = CalibratedClassifierCV(estimator=model, method='isotonic')
    calibrated_model.fit(X_val_final, y_val)

    # 보정된 확률 추출 후 점수 환산
    X_example_final = preprocessor_final.transform(example_df[top_features])
    prob_calibrated = calibrated_model.predict_proba(X_example_final)[0, 1]
    health_score = (1.0 - prob_calibrated) * 100.0
    print(f"[example inference] 건강 점수 (100점 만점): {health_score:.2f}점 / 100점")
    # 여기까지 ----------------------------------


if __name__ == "__main__":
    main()
