import sys
from pathlib import Path

# 모듈 경로 추가 (analysis/src 내부의 모듈 불러오기)
BASE_DIR = Path(__file__).resolve().parent
sys.path.append(str(BASE_DIR / "analysis" / "src"))

from user_turn_analysis import analyze_user_turn_audio

def run_test(audio_file_path: str):
    print(f"\n==========================================")
    print(f"테스트 음성 파일: {audio_file_path}")
    print(f"==========================================\n")

    # 1. user_turn_analysis의 analyze_user_turn_audio 함수 호출
    result = analyze_user_turn_audio(audio_file_path)

    # 2. 분석 실패 시 처리
    if not result.get("audio_analysis_available", False):
        print(f"분석 실패: {result.get('audio_analysis_error', '알 수 없는 오류')}")
        return

    # 3. 결과 딕셔너리에서 메타데이터(분석 결과)와 실제 음향 피처를 동적으로 분리
    meta_keys = {
        "audio_analysis_available",
        "audio_analysis_error",
        "predicted_label",
        "abnormal_probability",
        "speech_abnormality_score",
        "speech_abnormality_level"
    }

    # meta_keys에 포함되지 않은 나머지 키들을 실제 분석된 음향 피처로 간주
    extracted_features = {k: v for k, v in result.items() if k not in meta_keys}

    # 4. 결과 출력
    print("[분석 결과]")
    print(f" - 비정상 확률 (Abnormal Prob) : {result.get('abnormal_probability', 0.0) * 100:.2f}%")
    print(f" - 구음장애 위험도 점수 (0~15점) : {result.get('speech_abnormality_score', 0)} 점")
    print(f" - 위험도 레벨 (Level)          : {result.get('speech_abnormality_level', 'N/A')}")
    print("------------------------------------------")

    print(f" - 추출된 주요 피처 수 : {len(extracted_features)}개 (MFCC 및 eGeMAPS 등)")

    # 피처 추출이 정상적으로 이루어졌다면 샘플로 5개만 출력하여 확인
    if extracted_features:
        print(" - [추출된 피처 값 샘플 확인]")
        feature_items = list(extracted_features.items())
        for k, v in feature_items[:5]:
            if isinstance(v, float):
                print(f"   * {k}: {v:.4f}")
            else:
                print(f"   * {k}: {v}")

        if len(feature_items) > 5:
            print(f"   * ... 외 {len(feature_items) - 5}개 항목 정상 추출됨")
    print("==========================================\n")

if __name__ == "__main__":
    # 테스트 음성 파일 경로(.wav)
    TEST_AUDIO_PATH = "sample_test.wav"

    run_test(TEST_AUDIO_PATH)