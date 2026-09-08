# 수정 전 코드. 오류 많이 나면 아래 다 지우고 이거 사용

# from pathlib import Path
# import os
# import sys
# import pickle
# import tempfile
# import joblib
# from urllib.parse import urlparse
# from urllib.request import Request, urlopen
# import logging
# import requests
# import librosa
# import soundfile as sf
# from typing import Optional, Dict, Any
#
# import numpy as np
# import pandas as pd
#
# logger = logging.getLogger(__name__)
#
# from text_features import extract_text_features, calculate_basic_speech_features
# from baseline_speech_scoring import analyze_baseline_from_features
# from audio_features import extract_audio_features
# from speech_abnormality_scoring import (
#     calculate_dysarthria_similarity,
#     convert_similarity_to_reference_score,
# )
#
#
# # =========================
# # 0. 추론용 전처리기 및 예측 함수 (train_dysarthria_rf.py와 공유)
# # =========================
# # [수정] 기존에는 이 파일 안에 FeaturePreprocessor 클래스를 별도로 복사해서 정의하고,
# # joblib.load() 직전에 `sys.modules['__main__'].FeaturePreprocessor = FeaturePreprocessor`
# # 라는 몽키패치로 pickle 클래스 참조를 우회했다. 이 방식은 train_dysarthria_rf.py를
# # "python train_dysarthria_rf.py"로 직접 실행했을 때(클래스 __module__ == '__main__')만
# # 우연히 맞아떨어지고, import 기반으로 학습을 돌리면 깨지는 데다, 두 파일의 클래스 정의가
# # 조금이라도 어긋나면 추론이 조용히 틀어지는 위험이 있었다.
# # -> feature_preprocessor.py(공용 모듈)에서 동일한 클래스를 import해서 사용한다.
# sys.path.append(str(Path(__file__).resolve().parent))
# from feature_preprocessor import FeaturePreprocessor, predict_from_feature_dict  # noqa: E402, F401
#
#
# # =========================
# # 경로 설정
# # =========================
#
# AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
# MODEL_DIR = Path(os.getenv("MIDAS_REFERENCE_MODEL_DIR", AI_ANALYSIS_ROOT / "artifacts"))
#
# RF_MODEL_PATH = MODEL_DIR / "random_forest_model.joblib"
# PREPROCESSOR_PATH = MODEL_DIR / "preprocessor.joblib"
#
#
# # =========================
# # reference profile 로드
# # =========================
#
# def load_pickle_file(path: Path):
#     logger.info("load_pickle_file path=%s exists=%s", path, path.exists())
#     if not path.exists():
#         raise FileNotFoundError(f"필요한 reference 파일을 찾을 수 없습니다: {path}")
#
#     try:
#         data = joblib.load(path)
#     except Exception:
#         with open(path, "rb") as f:
#             data = pickle.load(f)
#
#     return data
#
#
# from functools import lru_cache
#
#
# @lru_cache(maxsize=1)
# def load_speech_abnormality_reference():
#     """
#     구음장애/발화 이상 Random Forest 모델과 전처리기(FeaturePreprocessor)를 로드한다.
#
#     [수정 1] 기존의 `sys.modules['__main__'].FeaturePreprocessor = FeaturePreprocessor`
#     몽키패치를 제거했다. feature_preprocessor.py를 train_dysarthria_rf.py와 공유하므로
#     joblib.load()가 항상 'feature_preprocessor.FeaturePreprocessor'라는 고정된 실제
#     모듈 경로를 찾게 되어 더 이상 필요 없다.
#     [수정 2] @lru_cache(maxsize=1)를 추가해 요청마다 디스크에서 모델을 다시 읽지 않고
#     프로세스당 1회만 로드하도록 했다 (fastapi_ai.py의 get_stt_pipeline()과 동일한 패턴).
#     """
#     logger.info("load_speech_abnormality_reference RF_MODEL_PATH=%s exists=%s",
#                 RF_MODEL_PATH, RF_MODEL_PATH.exists())
#     logger.info("load_speech_abnormality_reference PREPROCESSOR_PATH=%s exists=%s",
#                 PREPROCESSOR_PATH, PREPROCESSOR_PATH.exists())
#
#     if not RF_MODEL_PATH.exists():
#         raise FileNotFoundError(
#             f"필요한 RF 모델 파일을 찾을 수 없습니다: {RF_MODEL_PATH}\n"
#             f"-> train_dysarthria_rf.py를 실행해 모델을 학습하고, 그 산출물이 이 경로"
#             f"(환경변수 MIDAS_REFERENCE_MODEL_DIR 로 지정 가능)에 저장되어 있는지 확인하세요."
#         )
#
#     if not PREPROCESSOR_PATH.exists():
#         raise FileNotFoundError(f"필요한 preprocessor 파일을 찾을 수 없습니다: {PREPROCESSOR_PATH}")
#
#     model = joblib.load(RF_MODEL_PATH)
#     preprocessor = joblib.load(PREPROCESSOR_PATH)
#
#     logger.info("load_speech_abnormality_reference done model_type=%s preprocessor_type=%s",
#                 type(model).__name__, type(preprocessor).__name__)
#
#     return model, preprocessor
#
#
# # =========================
# # URL 경로 처리 함수
# # =========================
#
# def resolve_audio_input(
#         audio_path: Optional[str] = None,
#         audio_url: Optional[str] = None,
# ) -> Optional[str]:
#     logger.info("resolve_audio_input audio_path=%s audio_url=%s", audio_path, audio_url)
#
#     def _is_url(s: str) -> bool:
#         return isinstance(s, str) and (s.startswith("http://") or s.startswith("https://"))
#
#     def _download_to_temp(url: str) -> str:
#         logger.info("downloading from url=%s", url)
#         suffix = Path(url.split("?")[0]).suffix or ".wav"
#         response = requests.get(url, timeout=60)
#         response.raise_for_status()
#         temp_dir = Path(tempfile.gettempdir()) / "midas_audio"
#         temp_dir.mkdir(parents=True, exist_ok=True)
#         temp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix, dir=temp_dir)
#         with temp:
#             temp.write(response.content)
#         return temp.name
#
#     if audio_path is not None and str(audio_path).strip() != "":
#         path_str = str(audio_path).strip()
#         if _is_url(path_str):
#             return _download_to_temp(path_str)
#
#         path = Path(path_str)
#         if path.exists():
#             return str(path)
#
#     if audio_url is not None and str(audio_url).strip() != "":
#         url_str = str(audio_url).strip()
#         if _is_url(url_str):
#             return _download_to_temp(url_str)
#
#     return None
#
#
# # =========================
# # 점수 변환 함수
# # =========================
#
# def normalize_speech_score(raw_score: float, max_score: float = 40.0) -> float:
#     if max_score <= 0:
#         return 0.0
#
#     normalized = (raw_score / max_score) * 100
#     normalized = max(0.0, min(normalized, 100.0))
#
#     return round(normalized, 2)
#
#
# def convert_score_to_level(score: float) -> str:
#     if score >= 70:
#         return "High"
#     elif score >= 40:
#         return "Medium"
#     else:
#         return "Low"
#
#
# # =========================
# # baseline 분석
# # =========================
#
# def analyze_user_turn_baseline(
#         record_id: int,
#         session_id: Optional[int],
#         transcript_text: str,
#         duration_sec: float,
# ) -> Dict[str, Any]:
#     text_features = extract_text_features(transcript_text)
#     speech_features = calculate_basic_speech_features(
#         text_features,
#         duration_sec
#     )
#     baseline_result = analyze_baseline_from_features(
#         word_count=text_features["word_count"],
#         speech_rate_word=speech_features["speech_rate_word"],
#         record_time_float=speech_features["record_time_float"],
#     )
#
#     return {
#         "recordId": record_id,
#         "sessionId": session_id,
#         "transcriptText": transcript_text,
#         **text_features,
#         **speech_features,
#         **baseline_result,
#     }
#
# # 구음 장애 데이터셋 문장 단위 잘랐을 때 정확도 낮으면 사용. 청크 단위 음성 나눈 후 추출, 분석
# # def get_vad_audio_chunks(audio_path: str, chunk_duration: float = 3.0):
# #     """
# #     오디오에서 묵음을 제거(VAD)한 뒤, 순수 발화 구간만 이어붙여
# #     3초 단위의 Chunk 배열로 반환합니다.
# #     """
# #     # 1. 오디오 로드 (16kHz 통일)
# #     y, sr = librosa.load(audio_path, sr=16000)
# #
# #     # 2. 묵음 제거 (VAD) - 30dB 기준으로 실제 발화 구간(intervals)만 추출
# #     intervals = librosa.effects.split(y, top_db=30)
# #     if len(intervals) == 0:
# #         return [], sr
# #
# #     # 3. 순수 발화 신호 결합
# #     active_y = np.concatenate([y[start:end] for start, end in intervals])
# #
# #     # 4. 3초(chunk_duration * sr) 단위로 자르기
# #     chunk_size = int(chunk_duration * sr)
# #     chunks = []
# #
# #     for i in range(0, len(active_y), chunk_size):
# #         chunk = active_y[i:i + chunk_size]
# #         # 마지막 조각이 너무 짧으면(1.5초 미만) 버리고, 길면 패딩하여 3초 규격 맞춤
# #         if len(chunk) >= chunk_size // 2:
# #             if len(chunk) < chunk_size:
# #                 chunk = np.pad(chunk, (0, chunk_size - len(chunk)), mode='constant')
# #             chunks.append(chunk)
# #
# #     return chunks, sr
#
# # =========================
# # 음향 이상 분석
# # =========================
#
# def analyze_user_turn_audio(
#         audio_path: str,
# ) -> Dict[str, Any]:
#     logger.info("analyze_user_turn_audio start audio_path=%s exists=%s",
#                 audio_path, os.path.exists(audio_path) if audio_path else False)
#
#     if audio_path is None or str(audio_path).strip() == "":
#         return {
#             "audio_analysis_available": False,
#             "audio_analysis_error": "audio_path가 비어 있습니다.",
#             "abnormal_probability": None,
#             "speech_abnormality_level": "Unknown",
#             "speech_abnormality_score": 0,
#         }
#
#     audio_path_obj = Path(audio_path)
#
#     if not audio_path_obj.exists():
#         return {
#             "audio_analysis_available": False,
#             "audio_analysis_error": f"음성 파일을 찾을 수 없습니다: {audio_path}",
#             "abnormal_probability": None,
#             "speech_abnormality_level": "Unknown",
#             "speech_abnormality_score": 0,
#         }
#
#     # 1. 파일에서 바로 eGeMAPS 피처 추출 (기존 원래 방식)
#     audio_features = extract_audio_features(str(audio_path_obj))
#
#     if audio_features is None:
#         return {
#             "audio_analysis_available": False,
#             "audio_analysis_error": "음향 특징 추출에 실패했습니다.",
#             "abnormal_probability": None,
#             "speech_abnormality_level": "Unknown",
#             "speech_abnormality_score": 0,
#         }
#
#     try:
#         # 2. 모델 및 전처리기 로드 후 바로 추론 (3초 루프 없이 단일 추론)
#         model, preprocessor = load_speech_abnormality_reference()
#         rf_result = predict_from_feature_dict(audio_features, model, preprocessor)
#         abnormal_prob = rf_result["abnormal_probability"]
#
#         speech_abnormality_score = round(abnormal_prob * 15, 2)
#
#         if abnormal_prob >= 0.7:
#             speech_abnormality_level = "High"
#         elif abnormal_prob >= 0.4:
#             speech_abnormality_level = "Medium"
#         else:
#             speech_abnormality_level = "Low"
#
#         score_result = {
#             "abnormal_probability": round(abnormal_prob, 4),
#             "predicted_label": rf_result["predicted_label"],
#             "speech_abnormality_level": speech_abnormality_level,
#             "speech_abnormality_score": speech_abnormality_score,
#             "reference_similarity_level": speech_abnormality_level,
#             "reference_similarity_score": speech_abnormality_score,
#         }
#
#         result = {
#             "audio_analysis_available": True,
#             **audio_features,
#             **score_result,
#         }
#         return result
#
#     except Exception as e:
#         logger.exception("analyze_user_turn_audio exception audio_path=%s", audio_path)
#         return {
#             "audio_analysis_available": False,
#             "audio_analysis_error": str(e),
#             **audio_features,
#             "abnormal_probability": None,
#             "speech_abnormality_level": "Unknown",
#             "speech_abnormality_score": 0,
#         }
#
#
# # =========================
# # 사용자 답변 1개 통합 분석
# # =========================
#
# def analyze_user_turn(
#         record_id: int,
#         session_id: Optional[int],
#         audio_path: Optional[str] = None,
#         audio_url: Optional[str] = None,
#         transcript_text: str = "",
#         duration_sec: float = 0,
# ) -> Dict[str, Any]:
#
#     baseline_result = analyze_user_turn_baseline(
#         record_id=record_id,
#         session_id=session_id,
#         transcript_text=transcript_text,
#         duration_sec=duration_sec,
#     )
#
#     try:
#         resolved_audio_path = resolve_audio_input(
#             audio_path=audio_path,
#             audio_url=audio_url,
#         )
#     except Exception as e:
#         resolved_audio_path = None
#         audio_result = {
#             "audio_analysis_available": False,
#             "audio_analysis_error": str(e),
#             "dysarthria_similarity_score": None,
#             "distance_from_reference": None,
#             "speech_abnormality_level": "Unknown",
#             "speech_abnormality_score": 0,
#         }
#     else:
#         audio_result = analyze_user_turn_audio(resolved_audio_path)
#
#     effective_duration_sec = duration_sec or 0
#     try:
#         effective_duration_sec = float(effective_duration_sec)
#     except (TypeError, ValueError):
#         effective_duration_sec = 0
#
#     audio_duration = audio_result.get("audio_duration")
#     if effective_duration_sec <= 0 and audio_duration:
#         effective_duration_sec = float(audio_duration)
#
#     if effective_duration_sec > 0 and effective_duration_sec != baseline_result.get("record_time_float"):
#         baseline_result = analyze_user_turn_baseline(
#             record_id=record_id,
#             session_id=session_id,
#             transcript_text=transcript_text,
#             duration_sec=effective_duration_sec,
#         )
#
#     speech_duration = audio_result.get("speech_duration")
#     if speech_duration and speech_duration > 0:
#         audio_result["articulation_rate_word"] = round(
#             baseline_result.get("word_count", 0) / speech_duration,
#             3,
#             )
#
#     raw_speech_score = (
#             baseline_result.get("baseline_speech_score", 0)
#             + audio_result.get("speech_abnormality_score", 0)
#     )
#
#     speech_risk_score = normalize_speech_score(
#         raw_score=raw_speech_score,
#         max_score=40.0,
#     )
#
#     speech_risk_level = convert_score_to_level(speech_risk_score)
#     speech_health_score = round(100.0 - speech_risk_score, 2)
#
#     return {
#         **baseline_result,
#         **audio_result,
#         "audioPath": audio_path,
#         "audioUrl": audio_url,
#         "resolvedAudioPath": resolved_audio_path,
#         "raw_speech_score": raw_speech_score,
#         "speechRiskScore": speech_risk_score,
#         "speechHealthScore": speech_health_score,
#         "speechRiskLevel": speech_risk_level,
#     }
#
#
# def main():
#     result = analyze_user_turn(
#         record_id=1,
#         session_id=1,
#         audio_path="",
#         audio_url="",
#         transcript_text="밥 먹었어요",
#         duration_sec=7.0,
#     )
#     print("사용자 답변 통합 분석 결과")
#     print(result)
#
#
# if __name__ == "__main__":
#     main()

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
from typing import Optional, Dict, Any, Tuple

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

from text_features import extract_text_features, calculate_basic_speech_features
from baseline_speech_scoring import analyze_baseline_from_features
from audio_features import extract_audio_features
from speech_abnormality_scoring import (
    calculate_dysarthria_similarity,
    convert_similarity_to_reference_score,
)

# =========================
# 0. 추론용 전처리기 및 예측 함수
# =========================
sys.path.append(str(Path(__file__).resolve().parent))
from feature_preprocessor import FeaturePreprocessor, predict_from_feature_dict  # noqa: E402, F401


# =========================
# 경로 및 점수 체계 임계값(Cut-off) 설정
# =========================

AI_ANALYSIS_ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = Path(os.getenv("MIDAS_REFERENCE_MODEL_DIR", AI_ANALYSIS_ROOT / "artifacts"))

RF_MODEL_PATH = MODEL_DIR / "random_forest_model.joblib"
PREPROCESSOR_PATH = MODEL_DIR / "preprocessor.joblib"

# Youden's Index 및 ROC 분석으로 설정할 확률 임계값 (프로젝트 검증 데이터셋 수치로 조정 가능)
PROB_CUTOFF_CAUTION: float = 0.35    # 확률 < 0.35: 정상 (Low Risk)
PROB_CUTOFF_ABNORMAL: float = 0.65   # 확률 >= 0.65: 비정상 (High Risk), 그 사이: 주의 (Medium Risk)


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


from functools import lru_cache


@lru_cache(maxsize=1)
def load_speech_abnormality_reference():
    """
    구음장애/발화 이상 확률보정 모델(Calibrated Model)과 전처리기(FeaturePreprocessor)를 로드합니다.
    """
    logger.info("load_speech_abnormality_reference RF_MODEL_PATH=%s exists=%s",
                RF_MODEL_PATH, RF_MODEL_PATH.exists())
    logger.info("load_speech_abnormality_reference PREPROCESSOR_PATH=%s exists=%s",
                PREPROCESSOR_PATH, PREPROCESSOR_PATH.exists())

    if not RF_MODEL_PATH.exists():
        raise FileNotFoundError(
            f"필요한 RF 모델 파일을 찾을 수 없습니다: {RF_MODEL_PATH}\n"
            f"-> train_dysarthria_rf.py를 실행해 모델을 학습하고, 그 산출물이 이 경로에 있는지 확인하세요."
        )

    if not PREPROCESSOR_PATH.exists():
        raise FileNotFoundError(f"필요한 preprocessor 파일을 찾을 수 없습니다: {PREPROCESSOR_PATH}")

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
# 점수 보정 및 변환 핵심 로직 (Probability Calibration + Piecewise Linear Scaling)
# =========================

def map_calibrated_prob_to_health_score(prob_abnormal: float) -> Tuple[float, str]:
    """
    보정된 비정상 확률(prob_abnormal)을 100점 만점 건강 점수와 위험도 등급으로 매핑합니다.
    (Piecewise Linear Scaling 사용)

    - 확률 < PROB_CUTOFF_CAUTION (0.35) -> 점수 80 ~ 100점 (Low Risk / 정상)
    - 0.35 <= 확률 < PROB_CUTOFF_ABNORMAL (0.65) -> 점수 50 ~ 79점 (Medium Risk / 주의)
    - 확률 >= PROB_CUTOFF_ABNORMAL (0.65) -> 점수 0 ~ 49점 (High Risk / 비정상)
    """
    prob = float(np.clip(prob_abnormal, 0.0, 1.0))

    if prob < PROB_CUTOFF_CAUTION:
        # 정상 구간 [0.0, CUTOFF_CAUTION] -> [100.0, 80.0] 매핑
        ratio = prob / PROB_CUTOFF_CAUTION
        health_score = 100.0 - (ratio * 20.0)
        risk_level = "Low"
    elif prob < PROB_CUTOFF_ABNORMAL:
        # 주의 구간 [CUTOFF_CAUTION, CUTOFF_ABNORMAL] -> [79.0, 50.0] 매핑
        ratio = (prob - PROB_CUTOFF_CAUTION) / (PROB_CUTOFF_ABNORMAL - PROB_CUTOFF_CAUTION)
        health_score = 79.0 - (ratio * 29.0)
        risk_level = "Medium"
    else:
        # 비정상 구간 [CUTOFF_ABNORMAL, 1.0] -> [49.0, 0.0] 매핑
        ratio = (prob - PROB_CUTOFF_ABNORMAL) / (1.0 - PROB_CUTOFF_ABNORMAL)
        health_score = 49.0 - (ratio * 49.0)
        risk_level = "High"

    return round(health_score, 2), risk_level


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


# =========================
# 음향 이상 분석 (Probability Calibration 반영)
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
            "audio_health_score": 0.0,
        }

    audio_path_obj = Path(audio_path)

    if not audio_path_obj.exists():
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": f"음성 파일을 찾을 수 없습니다: {audio_path}",
            "abnormal_probability": None,
            "speech_abnormality_level": "Unknown",
            "audio_health_score": 0.0,
        }

    # 1. eGeMAPS 피처 추출
    audio_features = extract_audio_features(str(audio_path_obj))

    if audio_features is None:
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": "음향 특징 추출에 실패했습니다.",
            "abnormal_probability": None,
            "speech_abnormality_level": "Unknown",
            "audio_health_score": 0.0,
        }

    try:
        # 2. 보정된 모델 및 전처리기 로드
        model, preprocessor = load_speech_abnormality_reference()
        rf_result = predict_from_feature_dict(audio_features, model, preprocessor)

        # 확률 보정된 비정상 확률 (Calibrated Probability)
        calibrated_prob = float(rf_result["abnormal_probability"])

        # 3. Cut-off 기반 점수 및 등급 매핑
        audio_health_score, speech_abnormality_level = map_calibrated_prob_to_health_score(calibrated_prob)

        score_result = {
            "abnormal_probability": round(calibrated_prob, 4),
            "predicted_label": rf_result["predicted_label"],
            "speech_abnormality_level": speech_abnormality_level,  # Low(정상), Medium(주의), High(비정상)
            "audio_health_score": audio_health_score,              # 0~100점 만점 음향 건강 점수
            "speech_abnormality_score": round((100.0 - audio_health_score) * 0.15, 2), # 기존 하위 호환용 필드
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
            "audio_health_score": 0.0,
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

    # 1. 텍스트/발화 베이스라인 분석
    baseline_result = analyze_user_turn_baseline(
        record_id=record_id,
        session_id=session_id,
        transcript_text=transcript_text,
        duration_sec=duration_sec,
    )

    # 2. 오디오 음향 분석
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
            "speech_abnormality_level": "Unknown",
            "audio_health_score": 0.0,
        }
    else:
        audio_result = analyze_user_turn_audio(resolved_audio_path)

    # 3. 발화 시간 및 조음 속도 재보정
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

    # 4. 종합 음성 건강 점수(speechHealthScore) 및 위험도 등급(speechRiskLevel) 계산
    if audio_result.get("audio_analysis_available", False):
        audio_health = audio_result.get("audio_health_score", 100.0)
        # 베이스라인 점수를 100점 만점으로 스케일링 후 종합 (오디오 60% + 베이스라인 40%)
        baseline_score = float(baseline_result.get("baseline_speech_score", 0))
        baseline_health = max(0.0, min(100.0, (1.0 - (baseline_score / 25.0)) * 100.0))

        speech_health_score = round((audio_health * 0.6) + (baseline_health * 0.4), 2)
    else:
        # 오디오 분석 불가 시 베이스라인 점수로 대체
        baseline_score = float(baseline_result.get("baseline_speech_score", 0))
        speech_health_score = round(max(0.0, min(100.0, (1.0 - (baseline_score / 25.0)) * 100.0)), 2)

    # 최종 위험도 점수 및 위험 등급 결정
    speech_risk_score = round(100.0 - speech_health_score, 2)

    if speech_health_score >= 80.0:
        speech_risk_level = "Low"      # 정상 (건강점수 80점 이상)
    elif speech_health_score >= 50.0:
        speech_risk_level = "Medium"   # 주의 (건강점수 50~79점)
    else:
        speech_risk_level = "High"     # 비정상 (건강점수 50점 미만)

    return {
        **baseline_result,
        **audio_result,
        "audioPath": audio_path,
        "audioUrl": audio_url,
        "resolvedAudioPath": resolved_audio_path,
        "speechRiskScore": speech_risk_score,       # 음성 위험도 점수 (0~100)
        "speechHealthScore": speech_health_score,   # 음성 건강 점수 (0~100, 높은 수치가 좋은 건강 상태)
        "speechRiskLevel": speech_risk_level,       # Low(정상), Medium(주의), High(비정상)
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