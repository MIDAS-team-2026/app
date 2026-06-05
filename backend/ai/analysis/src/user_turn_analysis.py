from pathlib import Path
import pickle
import tempfile
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from typing import Optional, Dict, Any

from text_features import extract_text_features, calculate_basic_speech_features
from baseline_speech_scoring import analyze_baseline_from_features

from audio_features import extract_audio_features
from speech_abnormality_scoring import (
    calculate_dysarthria_similarity,
    convert_similarity_to_reference_score,
)


# =========================
# 경로 설정
# =========================
# 실제 reference profile 파일은 GitHub에 올리지 않고 로컬에만 둔다.
# build_reference_model.py 또는 build_reference_features.py 실행 후 생성되는 파일을 기준으로 사용한다.

MODEL_DIR = Path(r"D:\MIDAS_EXTRACTED\model")

REFERENCE_MEAN_PATH = MODEL_DIR / "reference_mean.pkl"
REFERENCE_STD_PATH = MODEL_DIR / "reference_std.pkl"
THRESHOLDS_PATH = MODEL_DIR / "thresholds.pkl"


# =========================
# reference profile 로드
# =========================

def load_pickle_file(path: Path):
    """
    pickle 파일을 로드한다.
    """
    if not path.exists():
        raise FileNotFoundError(f"필요한 reference 파일을 찾을 수 없습니다: {path}")

    with open(path, "rb") as f:
        return pickle.load(f)


def load_speech_abnormality_reference():
    """
    구음장애/발화 이상 참고군 profile을 로드한다.

    반환:
    - reference_mean
    - reference_std
    - thresholds

    thresholds는 없을 경우 기본값을 사용한다.
    """
    reference_mean = load_pickle_file(REFERENCE_MEAN_PATH)
    reference_std = load_pickle_file(REFERENCE_STD_PATH)

    if THRESHOLDS_PATH.exists():
        thresholds = load_pickle_file(THRESHOLDS_PATH)
    else:
        # demo용 기본값
        # 실제 프로젝트에서는 reference 내부 분포 기반 threshold를 사용하는 것이 좋다.
        thresholds = {
            "low_threshold": 0.49,
            "high_threshold": 0.52,
        }

    return reference_mean, reference_std, thresholds

# =========================
# URL 경로 처리 함수
# =========================

def resolve_audio_input(
    audio_path: Optional[str] = None,
    audio_url: Optional[str] = None,
) -> Optional[str]:
    """
    audio_path 또는 audio_url을 받아서 음향 분석에 사용할 로컬 파일 경로를 반환한다.

    - audio_path가 있으면 해당 로컬 파일 경로를 그대로 사용한다.
    - audio_url이 있으면 임시 폴더에 다운로드한 뒤 그 파일 경로를 반환한다.
    - 둘 다 없으면 None을 반환한다.

    주의:
    audio_url이 private S3 URL이면 presigned URL처럼 Python에서 접근 가능한 URL이어야 한다.
    """

    # 1. 로컬 파일 경로가 들어온 경우
    if audio_path is not None and str(audio_path).strip() != "":
        path = Path(audio_path)

        if path.exists():
            return str(path)

        raise FileNotFoundError(f"음성 파일을 찾을 수 없습니다: {audio_path}")

    # 2. URL이 들어온 경우
    if audio_url is not None and str(audio_url).strip() != "":
        parsed_url = urlparse(audio_url)
        file_name = Path(parsed_url.path).name

        if file_name == "":
            file_name = "midas_audio.wav"

        temp_dir = Path(tempfile.gettempdir()) / "midas_audio"
        temp_dir.mkdir(parents=True, exist_ok=True)

        temp_audio_path = temp_dir / file_name

        request = Request(audio_url, headers={"User-Agent": "Python-urllib"})
        with urlopen(request, timeout=30) as response:
            content = response.read()

        with open(temp_audio_path, "wb") as f:
            f.write(content)

        return str(temp_audio_path)

    # 3. 둘 다 없는 경우
    return None


# =========================
# 점수 변환 함수
# =========================

def normalize_speech_score(raw_score: float, max_score: float = 40.0) -> float:
    """
    record 단위 raw speech score를 0~100 점수로 변환한다.

    현재 raw score 구성 예시:
    - baseline_speech_score 최대 25
    - speech_abnormality_score 최대 15
    - 총 최대 40

    최종 risk 계산에서 recall_score와 같은 0~100 범위로 맞추기 위해 사용한다.
    """
    if max_score <= 0:
        return 0.0

    normalized = (raw_score / max_score) * 100
    normalized = max(0.0, min(normalized, 100.0))

    return round(normalized, 2)


def convert_score_to_level(score: float) -> str:
    """
    0~100 점수를 Low / Medium / High로 변환한다.
    """
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
    """
    사용자 답변 1개를 노인 자유대화 baseline 기준으로 분석한다.

    입력:
    - record_id: AudioRecord ID
    - session_id: 채팅 세션 ID
    - transcript_text: STT 변환 텍스트
    - duration_sec: 녹음 길이

    출력:
    - 텍스트 특징
    - 발화 속도 특징
    - baseline_speech_score
    - baseline_speech_level
    - baseline_reasons
    """

    # 1. STT 텍스트 기반 특징 추출
    text_features = extract_text_features(transcript_text)

    # 2. 녹음 시간 기반 말속도 특징 추출
    speech_features = calculate_basic_speech_features(
        text_features,
        duration_sec
    )

    # 3. 노인 자유대화 baseline 기준 점수화
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
# 음향 이상 분석
# =========================

def analyze_user_turn_audio(
    audio_path: str,
) -> Dict[str, Any]:
    """
    사용자 음성 파일을 구음장애/발화 이상 참고군과 비교하여
    음향 이상 유사도 점수를 계산한다.

    입력:
    - audio_path: 사용자 음성 파일 경로

    출력:
    - audio feature
    - dysarthria_similarity_score
    - distance_from_reference
    - speech_abnormality_level
    - speech_abnormality_score
    """

    if audio_path is None or str(audio_path).strip() == "":
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": "audio_path가 비어 있습니다.",
            "dysarthria_similarity_score": None,
            "distance_from_reference": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }

    audio_path_obj = Path(audio_path)

    if not audio_path_obj.exists():
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": f"음성 파일을 찾을 수 없습니다: {audio_path}",
            "dysarthria_similarity_score": None,
            "distance_from_reference": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }

    # 1. 음향 특징 추출
    audio_features = extract_audio_features(str(audio_path_obj))

    if audio_features is None:
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": "음향 특징 추출에 실패했습니다.",
            "dysarthria_similarity_score": None,
            "distance_from_reference": None,
            "speech_abnormality_level": "Unknown",
            "speech_abnormality_score": 0,
        }

    try:
        # 2. 구음장애 참고군 profile 로드
        reference_mean, reference_std, thresholds = load_speech_abnormality_reference()

        # 3. 참고군 유사도 계산
        similarity_result = calculate_dysarthria_similarity(
            audio_features,
            reference_mean,
            reference_std,
        )

        # 4. 유사도를 점수/레벨로 변환
        reference_score_result = convert_similarity_to_reference_score(
            similarity_result["dysarthria_similarity_score"],
            thresholds["low_threshold"],
            thresholds["high_threshold"],
)
        score_result = {
            "speech_abnormality_level": reference_score_result["reference_similarity_level"],
            "speech_abnormality_score": reference_score_result["reference_similarity_score"],
            "reference_similarity_level": reference_score_result["reference_similarity_level"],
            "reference_similarity_score": reference_score_result["reference_similarity_score"],
}

        return {
            "audio_analysis_available": True,
            **audio_features,
            **similarity_result,
            **score_result,
        }

    except Exception as e:
        return {
            "audio_analysis_available": False,
            "audio_analysis_error": str(e),
            **audio_features,
            "dysarthria_similarity_score": None,
            "distance_from_reference": None,
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
    """
    사용자 답변 1개를 통합 분석한다.

    입력:
    - record_id: AudioRecord ID
    - session_id: 세션 ID
    - audio_path: 사용자 음성 파일 로컬 경로
    - audio_url: 사용자 음성 파일 URL 또는 S3 presigned URL
    - transcript_text: 백엔드 STT 결과 텍스트
    - duration_sec: 녹음 길이

    출력:
    - baseline_speech_score
    - speech_abnormality_score
    - raw_speech_score
    - speechRiskScore
    - speechRiskLevel

    주의:
    이 결과는 의학적 진단이 아니라,
    자유대화 기반 인지기능 저하 위험도 산출에 사용되는 보조 지표이다.
    """

    # 1. 텍스트/발화 시간 기반 baseline 분석
    baseline_result = analyze_user_turn_baseline(
        record_id=record_id,
        session_id=session_id,
        transcript_text=transcript_text,
        duration_sec=duration_sec,
    )

    # 2. audio_path 또는 audio_url을 로컬 파일 경로로 변환
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
        # 3. 음성 파일 기반 발화 이상 분석
        audio_result = analyze_user_turn_audio(resolved_audio_path)

    # 4. raw score는 baseline + 음향 이상 점수의 합으로 정의
    raw_speech_score = (
        baseline_result.get("baseline_speech_score", 0)
        + audio_result.get("speech_abnormality_score", 0)
    )

    # 5. recall_score와 합치기 위해 0~100 범위로 정규화
    speech_risk_score = normalize_speech_score(
        raw_score=raw_speech_score,
        max_score=40.0,
    )

    speech_risk_level = convert_score_to_level(speech_risk_score)

    # 기존 회상 파트 calculate_final_risk_score()에 넣기 위한 점수
    # speechRiskScore는 높을수록 위험, speechHealthScore는 높을수록 양호
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


# =========================
# 테스트 실행
# =========================

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