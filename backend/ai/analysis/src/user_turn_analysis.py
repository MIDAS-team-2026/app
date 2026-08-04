from pathlib import Path
import os
import sys
import pickle
import tempfile
import joblib
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import logging
import requests
import librosa
import soundfile as sf
from typing import Optional, Dict, Any

import numpy as np
import pandas as pd
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler

logger = logging.getLogger(__name__)

from text_features import extract_text_features, calculate_basic_speech_features
from baseline_speech_scoring import analyze_baseline_from_features
from audio_features import extract_audio_features
from speech_abnormality_scoring import (
    calculate_dysarthria_similarity,
    convert_similarity_to_reference_score,
)

# =========================
# 0. 추론용 전처리기 및 예측 함수 정의 (독립 실행)
# =========================

class FeaturePreprocessor:
    def __init__(self, skew_threshold=0.75):
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

def predict_from_feature_dict(feature_dict: dict, model, preprocessor: FeaturePreprocessor):
    """
    추출된 피처 딕셔너리를 받아 RF 모델로부터 비정상 확률을 예측합니다.
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


# =========================
# 경로 설정
# =========================

AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = Path(os.getenv("MIDAS_REFERENCE_MODEL_DIR", AI_ANALYSIS_ROOT / "artifacts"))

RF_MODEL_PATH = MODEL_DIR / "random_forest_model.joblib"
PREPROCESSOR_PATH = MODEL_DIR / "preprocessor.joblib"


# =========================
# reference profile 로드
# =========================

def load_pickle_file(path: Path):
    logger.info("load_pickle_file path=%s exists=%s", path, path.exists())
    if not path.exists():
        raise FileNotFoundError(f"필요한 reference 파일을 찾을 수 없습니다: {path}")

    try:
        data = joblib.load(path)
    except Exception:
        with open(path, "rb") as f:
            data = pickle.load(f)

    return data


def load_speech_abnormality_reference():
    """
    구음장애/발화 이상 Random Forest 모델과 전처리기(FeaturePreprocessor)를 로드한다.
    """
    logger.info("load_speech_abnormality_reference RF_MODEL_PATH=%s exists=%s",
                RF_MODEL_PATH, RF_MODEL_PATH.exists())
    logger.info("load_speech_abnormality_reference PREPROCESSOR_PATH=%s exists=%s",
                PREPROCESSOR_PATH, PREPROCESSOR_PATH.exists())

    if not RF_MODEL_PATH.exists():
        raise FileNotFoundError(f"필요한 RF 모델 파일을 찾을 수 없습니다: {RF_MODEL_PATH}")

    if not PREPROCESSOR_PATH.exists():
        raise FileNotFoundError(f"필요한 preprocessor 파일을 찾을 수 없습니다: {PREPROCESSOR_PATH}")

    sys.modules['__main__'].FeaturePreprocessor = FeaturePreprocessor

    model = joblib.load(RF_MODEL_PATH)
    preprocessor = joblib.load(PREPROCESSOR_PATH)

    logger.info("load_speech_abnormality_reference done model_type=%s preprocessor_type=%s",
                type(model).__name__, type(preprocessor).__name__)

    return model, preprocessor


# =========================
# URL 경로 처리 함수
# =========================

def resolve_audio_input(
        audio_path: Optional[str] = None,
        audio_url: Optional[str] = None,
) -> Optional[str]:
    logger.info("resolve_audio_input audio_path=%s audio_url=%s", audio_path, audio_url)

    def _is_url(s: str) -> bool:
        return isinstance(s, str) and (s.startswith("http://") or s.startswith("https://"))

    def _download_to_temp(url: str) -> str:
        logger.info("downloading from url=%s", url)
        suffix = Path(url.split("?")[0]).suffix or ".wav"
        response = requests.get(url, timeout=60)
        response.raise_for_status()
        temp_dir = Path(tempfile.gettempdir()) / "midas_audio"
        temp_dir.mkdir(parents=True, exist_ok=True)
        temp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix, dir=temp_dir)
        with temp:
            temp.write(response.content)
        return temp.name

    if audio_path is not None and str(audio_path).strip() != "":
        path_str = str(audio_path).strip()
        if _is_url(path_str):
            return _download_to_temp(path_str)

        path = Path(path_str)
        if path.exists():
            return str(path)

    if audio_url is not None and str(audio_url).strip() != "":
        url_str = str(audio_url).strip()
        if _is_url(url_str):
            return _download_to_temp(url_str)

    return None


# =========================
# 점수 변환 함수
# =========================

def normalize_speech_score(raw_score: float, max_score: float = 40.0) -> float:
    if max_score <= 0:
        return 0.0

    normalized = (raw_score / max_score) * 100
    normalized = max(0.0, min(normalized, 100.0))

    return round(normalized, 2)


def convert_score_to_level(score: float) -> str:
    if score >= 70:
        return "High"
    elif score >= 40:
        return "Medium"
    else:
        return "Low"


# =========================
# baseline 분석
# =========================

def analyze_user_turn_baseline(
        record_id: int,
        session_id: Optional[int],
        transcript_text: str,
        duration_sec: float,
) -> Dict[str, Any]:
    text_features = extract_text_features(transcript_text)
    speech_features = calculate_basic_speech_features(
        text_features,
        duration_sec
    )
    baseline_result = analyze_baseline_from_features(
        word_count=text_features["word_count"],
        speech_rate_word=speech_features["speech_rate_word"],
        record_time_float=speech_features["record_time_float"],
    )

    return {
        "recordId": record_id,
        "sessionId": session_id,
        "transcriptText": transcript_text,
        **text_features,
        **speech_features,
        **baseline_result,
    }

# 구음 장애 데이터셋 문장 단위 잘랐을 때 정확도 낮으면 사용. 청크 단위 음성 나눈 후 추출, 분석
# def get_vad_audio_chunks(audio_path: str, chunk_duration: float = 3.0):
#     """
#     오디오에서 묵음을 제거(VAD)한 뒤, 순수 발화 구간만 이어붙여
#     3초 단위의 Chunk 배열로 반환합니다.
#     """
#     # 1. 오디오 로드 (16kHz 통일)
#     y, sr = librosa.load(audio_path, sr=16000)
#
#     # 2. 묵음 제거 (VAD) - 30dB 기준으로 실제 발화 구간(intervals)만 추출
#     intervals = librosa.effects.split(y, top_db=30)
#     if len(intervals) == 0:
#         return [], sr
#
#     # 3. 순수 발화 신호 결합
#     active_y = np.concatenate([y[start:end] for start, end in intervals])
#
#     # 4. 3초(chunk_duration * sr) 단위로 자르기
#     chunk_size = int(chunk_duration * sr)
#     chunks = []
#
#     for i in range(0, len(active_y), chunk_size):
#         chunk = active_y[i:i + chunk_size]
#         # 마지막 조각이 너무 짧으면(1.5초 미만) 버리고, 길면 패딩하여 3초 규격 맞춤
#         if len(chunk) >= chunk_size // 2:
#             if len(chunk) < chunk_size:
#                 chunk = np.pad(chunk, (0, chunk_size - len(chunk)), mode='constant')
#             chunks.append(chunk)
#
#     return chunks, sr

# =========================
# 음향 이상 분석
# =========================

def analyze_user_turn_audio(
        audio_path: str,
) -> Dict[str, Any]:
    logger.info("analyze_user_turn_audio start audio_path=%s exists=%s",
                audio_path, os.path.exists(audio_path) if audio_path else False)

    if audio_path is None or str(audio_path).strip() == "":
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": "audio_path가 비어 있습니다.",
            "abnormal_probability": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }

    audio_path_obj = Path(audio_path)

    if not audio_path_obj.exists():
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": f"음성 파일을 찾을 수 없습니다: {audio_path}",
            "abnormal_probability": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }

    # 1. 파일에서 바로 eGeMAPS 피처 추출 (기존 원래 방식)
    audio_features = extract_audio_features(str(audio_path_obj))

    if audio_features is None:
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": "음향 특징 추출에 실패했습니다.",
            "abnormal_probability": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }

    try:
        # 2. 모델 및 전처리기 로드 후 바로 추론 (3초 루프 없이 단일 추론)
        model, preprocessor = load_speech_abnormality_reference()
        rf_result = predict_from_feature_dict(audio_features, model, preprocessor)
        abnormal_prob = rf_result["abnormal_probability"]

        speech_abnormality_score = round(abnormal_prob * 15, 2)

        if abnormal_prob >= 0.7:
            speech_abnormality_level = "High"
        elif abnormal_prob >= 0.4:
            speech_abnormality_level = "Medium"
        else:
            speech_abnormality_level = "Low"

        score_result = {
            "abnormal_probability": round(abnormal_prob, 4),
            "predicted_label": rf_result["predicted_label"],
            "speech_abnormality_level": speech_abnormality_level,
            "speech_abnormality_score": speech_abnormality_score,
            "reference_similarity_level": speech_abnormality_level,
            "reference_similarity_score": speech_abnormality_score,
        }

        result = {
            "audio_analysis_available": True,
            **audio_features,
            **score_result,
        }
        return result

    except Exception as e:
        logger.exception("analyze_user_turn_audio exception audio_path=%s", audio_path)
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": str(e),
            **audio_features,
            "abnormal_probability": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }


# =========================
# 사용자 답변 1개 통합 분석
# =========================

def analyze_user_turn(
        record_id: int,
        session_id: Optional[int],
        audio_path: Optional[str] = None,
        audio_url: Optional[str] = None,
        transcript_text: str = "",
        duration_sec: float = 0,
) -> Dict[str, Any]:

    baseline_result = analyze_user_turn_baseline(
        record_id=record_id,
        session_id=session_id,
        transcript_text=transcript_text,
        duration_sec=duration_sec,
    )

    try:
        resolved_audio_path = resolve_audio_input(
            audio_path=audio_path,
            audio_url=audio_url,
        )
    except Exception as e:
        resolved_audio_path = None
        audio_result = {
            "audio_analysis_available": False,
            "audio_analysis_error": str(e),
            "dysarthria_similarity_score": None,
            "distance_from_reference": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }
    else:
        audio_result = analyze_user_turn_audio(resolved_audio_path)

    effective_duration_sec = duration_sec or 0
    try:
        effective_duration_sec = float(effective_duration_sec)
    except (TypeError, ValueError):
        effective_duration_sec = 0

    audio_duration = audio_result.get("audio_duration")
    if effective_duration_sec <= 0 and audio_duration:
        effective_duration_sec = float(audio_duration)

    if effective_duration_sec > 0 and effective_duration_sec != baseline_result.get("record_time_float"):
        baseline_result = analyze_user_turn_baseline(
            record_id=record_id,
            session_id=session_id,
            transcript_text=transcript_text,
            duration_sec=effective_duration_sec,
        )

    speech_duration = audio_result.get("speech_duration")
    if speech_duration and speech_duration > 0:
        audio_result["articulation_rate_word"] = round(
            baseline_result.get("word_count", 0) / speech_duration,
            3,
            )

    raw_speech_score = (
            baseline_result.get("baseline_speech_score", 0)
            + audio_result.get("speech_abnormality_score", 0)
    )

    speech_risk_score = normalize_speech_score(
        raw_score=raw_speech_score,
        max_score=40.0,
    )

    speech_risk_level = convert_score_to_level(speech_risk_score)
    speech_health_score = round(100.0 - speech_risk_score, 2)

    return {
        **baseline_result,
        **audio_result,
        "audioPath": audio_path,
        "audioUrl": audio_url,
        "resolvedAudioPath": resolved_audio_path,
        "raw_speech_score": raw_speech_score,
        "speechRiskScore": speech_risk_score,
        "speechHealthScore": speech_health_score,
        "speechRiskLevel": speech_risk_level,
    }


def main():
    result = analyze_user_turn(
        record_id=1,
        session_id=1,
        audio_path="",
        audio_url="",
        transcript_text="밥 먹었어요",
        duration_sec=7.0,
    )
    print("사용자 답변 통합 분석 결과")
    print(result)


if __name__ == "__main__":
    main()