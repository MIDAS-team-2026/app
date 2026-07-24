import logging
import librosa
import numpy as np
import soundfile as sf
import os

logger = logging.getLogger(__name__)


SILENCE_TOP_DB = float(os.getenv("MIDAS_SILENCE_TOP_DB", "30"))
MIN_PAUSE_DURATION_SEC = float(os.getenv("MIDAS_MIN_PAUSE_DURATION_SEC", "0.25"))


def _extract_features_from_signal(y, sr):
    """
    하나의 음성 segment에서 음향 특징을 추출하는 내부 함수입니다.
    """
    logger.debug("_extract_features_from_signal start y.shape=%s sr=%s", y.shape, sr)

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

    logger.debug("_extract_features_from_signal done keys=%s", list(features.keys()))
    return features


def _detect_voice_intervals(y, sr, frame_offset=0):
    """
    librosa의 energy 기반 split으로 음성 구간을 찾고, 전체 파일 기준 초 단위 구간으로 변환한다.
    """
    intervals = librosa.effects.split(
        y,
        top_db=SILENCE_TOP_DB,
        frame_length=2048,
        hop_length=512,
    )

    return [
        ((start + frame_offset) / sr, (end + frame_offset) / sr)
        for start, end in intervals
        if end > start
    ]


def _summarize_pause_features(voice_intervals, total_duration):
    """
    음성 구간 사이의 내부 무음 구간과 첫 발화 지연 시간을 요약한다.
    """
    if total_duration <= 0 or not voice_intervals:
        return {
            "pause_count": 0,
            "total_pause_duration": 0.0,
            "avg_pause_duration": 0.0,
            "max_pause_duration": 0.0,
            "pause_ratio": 0.0,
            "response_latency": round(float(total_duration), 3) if total_duration > 0 else 0.0,
            "speech_duration": 0.0,
            "voice_activity_ratio": 0.0,
        }

    sorted_intervals = sorted(voice_intervals, key=lambda interval: interval[0])
    merged_intervals = []

    for start, end in sorted_intervals:
        start = max(0.0, float(start))
        end = min(float(total_duration), float(end))

        if not merged_intervals or start > merged_intervals[-1][1]:
            merged_intervals.append([start, end])
        else:
            merged_intervals[-1][1] = max(merged_intervals[-1][1], end)

    internal_pauses = []
    for prev, current in zip(merged_intervals, merged_intervals[1:]):
        gap = current[0] - prev[1]
        if gap >= MIN_PAUSE_DURATION_SEC:
            internal_pauses.append(gap)

    speech_duration = sum(max(0.0, end - start) for start, end in merged_intervals)
    total_pause_duration = sum(internal_pauses)
    pause_count = len(internal_pauses)

    return {
        "pause_count": int(pause_count),
        "total_pause_duration": round(float(total_pause_duration), 3),
        "avg_pause_duration": round(float(total_pause_duration / pause_count), 3) if pause_count else 0.0,
        "max_pause_duration": round(float(max(internal_pauses)), 3) if internal_pauses else 0.0,
        "pause_ratio": round(float(total_pause_duration / total_duration), 3),
        "response_latency": round(float(merged_intervals[0][0]), 3),
        "speech_duration": round(float(speech_duration), 3),
        "voice_activity_ratio": round(float(speech_duration / total_duration), 3),
    }


def extract_audio_features(audio_path, segment_duration=60):
    """
    음성 파일 전체를 segment 단위로 나누어 음향 특징을 추출하는 함수입니다.

    전체 음성을 한 번에 메모리에 올리지 않고,
    60초 단위로 순차적으로 읽어 전체 음성을 분석합니다.
    """
    logger.info("extract_audio_features start path=%s", audio_path)

    try:
        info = sf.info(audio_path)
        sr = info.samplerate
        logger.info("sf.info done samplerate=%s frames=%s duration=%.2f", sr, info.frames, info.frames/sr)

        total_frames = info.frames
        total_duration = total_frames / sr

        segment_frames = int(segment_duration * sr)

        segment_features = []
        segment_count = 0
        voice_intervals = []
        frame_offset = 0

        with sf.SoundFile(audio_path) as audio_file:
            while True:
                y = audio_file.read(frames=segment_frames, dtype="float32")

                if len(y) == 0:
                    break

                # stereo인 경우 mono로 변환
                if y.ndim > 1:
                    y = np.mean(y, axis=1)

                current_frame_offset = frame_offset
                frame_offset += len(y)

                # 너무 짧은 segment는 제외
                if len(y) < sr * 0.5:
                    continue

                voice_intervals.extend(_detect_voice_intervals(y, sr, current_frame_offset))

                features = _extract_features_from_signal(y, sr)
                segment_features.append(features)
                segment_count += 1

        logger.info("segment loop done segment_count=%s", segment_count)

        if len(segment_features) == 0:
            logger.warning("no segment features extracted")
            return None

        feature_keys = segment_features[0].keys()

        final_features = {
            "audio_duration": float(total_duration),
            "segment_count": int(segment_count),
        }
        final_features.update(_summarize_pause_features(voice_intervals, total_duration))

        for key in feature_keys:
            values = np.array([seg[key] for seg in segment_features], dtype=float)
            final_features[key] = float(np.mean(values))

        logger.info("extract_audio_features done keys=%s", list(final_features.keys()))
        return final_features

    except Exception as e:
        logger.exception("extract_audio_features failed path=%s", audio_path)
        return None
