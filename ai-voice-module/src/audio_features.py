import librosa
import numpy as np


def extract_audio_features_60sec(audio_path):
    """
    음성 파일의 앞부분 60초 구간에서 음향 특징을 추출하는 함수

    초기 prototype 단계에서는 처리 시간과 메모리 사용량을 줄이기 위해
    각 음성 파일의 앞 60초만 사용합니다.

    향후 segment로 나누어 구간별 특징을 추출하고, 평균값 또는 최대 위험 점수를 종합하는 방식으로
    확장 예정
    """

    try:
        y, sr = librosa.load(audio_path, sr=None, duration=60)
        
        rms = librosa.feature.rms(y=y)[0]
        zcr = librosa.feature.zero_crossing_rate(y)[0]
        centroid = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
        mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)

        features = {
            "rms_mean": float(np.mean(rms)),
            "rms_std": float(np.std(rms)),
            "zcr_mean": float(np.mean(zcr)),
            "zcr_std": float(np.std(zcr)),
            "spectral_centroid_mean": float(np.mean(centroid)),
            "spectral_centroid_std": float(np.std(centroid)),
        }

        for i in range(13):
            features[f"mfcc_{i+1}_mean"] = float(np.mean(mfcc[i]))
            features[f"mfcc_{i+1}_std"] = float(np.std(mfcc[i]))

        return features

    except Exception as e:
        print(f"음향 특징 추출 실패: {audio_path}")
        print(e)
        return None