import librosa
import numpy as np
import soundfile as sf


def _extract_features_from_signal(y, sr):
    """
    하나의 음성 segment에서 음향 특징을 추출하는 내부 함수입니다.
    """

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


def extract_audio_features(audio_path, segment_duration=60):
    """
    음성 파일 전체를 segment 단위로 나누어 음향 특징을 추출하는 함수입니다.

    전체 음성을 한 번에 메모리에 올리지 않고,
    60초 단위로 순차적으로 읽어 전체 음성을 분석합니다.
    """

    try:
        info = sf.info(audio_path)
        sr = info.samplerate

        # sf.info() 결과는 len(info)로 길이를 구할 수 없습니다.
        # 전체 frame 수는 info.frames를 사용해야 합니다.
        total_frames = info.frames
        total_duration = total_frames / sr

        segment_frames = int(segment_duration * sr)

        segment_features = []
        segment_count = 0

        with sf.SoundFile(audio_path) as audio_file:
            while True:
                y = audio_file.read(frames=segment_frames, dtype="float32")

                if len(y) == 0:
                    break

                # stereo인 경우 mono로 변환
                if y.ndim > 1:
                    y = np.mean(y, axis=1)

                # 너무 짧은 segment는 제외
                if len(y) < sr * 0.5:
                    continue

                features = _extract_features_from_signal(y, sr)
                segment_features.append(features)
                segment_count += 1

        if len(segment_features) == 0:
            return None

        feature_keys = segment_features[0].keys()

        final_features = {
            "audio_duration": float(total_duration),
            "segment_count": int(segment_count),
        }

        for key in feature_keys:
            values = np.array([seg[key] for seg in segment_features], dtype=float)
            final_features[key] = float(np.mean(values))

        return final_features

    except Exception as e:
        print(f"음향 특징 추출 실패: {audio_path}")
        print(e)
        return None
