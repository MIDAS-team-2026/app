from pathlib import Path
import os
import json
import pandas as pd
from tqdm import tqdm

from text_features import extract_text_features, calculate_basic_speech_features


# =========================
# 노인 자유대화 챗봇 데이터 경로 설정
# =========================
# 현재 압축 해제된 실제 폴더 기준:
# 라벨: [라벨]1.AI챗봇
# 원천: [원천]1.AI챗봇_1, [원천]1.AI챗봇_2

ELDERLY_LABEL_ROOT = Path(
    r"D:\자유대화 음성(노인남녀)\Training\[라벨]1.AI챗봇"
)

ELDERLY_AUDIO_ROOTS = [
    Path(r"D:\자유대화 음성(노인남녀)\Training\[원천]1.AI챗봇_1"),
    Path(r"D:\자유대화 음성(노인남녀)\Training\[원천]1.AI챗봇_2"),
]

OUTPUT_DIR = Path(r"D:\MIDAS_EXTRACTED")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

OUTPUT_CSV = OUTPUT_DIR / "elderly_chatbot_features.csv"


def collect_wav_files(audio_roots):
    """
    원천데이터 폴더 여러 개에서 WAV 파일을 찾아
    파일명 기준 dictionary로 만든다.

    JSON의 fileNm 값과 실제 WAV 파일명을 매칭하기 위해 사용한다.
    """
    wav_dict = {}

    for audio_root in audio_roots:
        print("원천 경로:", audio_root)
        print("원천 경로 존재:", audio_root.exists())

        if not audio_root.exists():
            continue

        for root, dirs, files in os.walk(audio_root):
            for file in files:
                if file.lower().endswith(".wav"):
                    wav_dict[file] = str(Path(root) / file)

    return wav_dict


def find_json_files(label_root):
    """
    라벨링데이터 폴더에서 JSON 파일을 전부 찾는다.
    """
    json_files = []

    for root, dirs, files in os.walk(label_root):
        for file in files:
            if file.lower().endswith(".json"):
                json_files.append(Path(root) / file)

    return json_files


def load_elderly_json_files(label_root, audio_roots):
    """
    노인 자유대화 라벨링 JSON 파일을 읽고,
    원천 WAV 파일과 매칭한 뒤 텍스트/발화 특징을 추출한다.

    예상 JSON 구조:
    {
      "발화정보": {
        "stt": "...",
        "fileNm": "...wav",
        "recrdTime": "4.520"
      },
      "대화정보": {...},
      "녹음자정보": {...}
    }
    """

    print("라벨 경로:", label_root)
    print("라벨 경로 존재:", label_root.exists())

    if not label_root.exists():
        raise FileNotFoundError(f"라벨링데이터 경로를 찾을 수 없습니다: {label_root}")

    json_files = find_json_files(label_root)
    wav_dict = collect_wav_files(audio_roots)

    print("총 JSON 파일 수:", len(json_files))
    print("총 WAV 파일 수:", len(wav_dict))

    rows = []
    matched_count = 0
    not_matched_count = 0
    error_count = 0

    for json_path in tqdm(json_files, desc="노인 자유대화 JSON 처리 중"):
        try:
            with open(json_path, "r", encoding="utf-8") as f:
                data = json.load(f)

            utter_info = data.get("발화정보", {})
            conv_info = data.get("대화정보", {})
            speaker_info = data.get("녹음자정보", {})

            stt = utter_info.get("stt", "")
            record_time = utter_info.get("recrdTime", 0)
            audio_file_name = utter_info.get("fileNm", "")

            # JSON의 fileNm과 원천 WAV 파일명 매칭
            audio_path = wav_dict.get(audio_file_name, "NOT_FOUND")

            if audio_path == "NOT_FOUND":
                not_matched_count += 1
            else:
                matched_count += 1

            # 텍스트 특징 추출
            text_feats = extract_text_features(stt)

            # 녹음 시간 기반 발화 특징 추출
            speech_feats = calculate_basic_speech_features(text_feats, record_time)

            row = {
                # 파일 정보
                "json_path": str(json_path),
                "audio_file_name": audio_file_name,
                "audio_path": audio_path,
                "matched": 0 if audio_path == "NOT_FOUND" else 1,

                # 발화 정보
                "script_id": utter_info.get("scriptId", ""),
                "transcript": stt,
                "record_time": record_time,
                "record_quality": utter_info.get("recrdQuality", ""),
                "record_date": utter_info.get("recrdDt", ""),
                "script_set_no": utter_info.get("scriptSetNo", ""),

                # 대화 정보
                "record_environment": conv_info.get("recrdEnvrn", ""),
                "collection_unit_code": conv_info.get("colctUnitCode", ""),
                "city_code": conv_info.get("cityCode", ""),
                "record_unit": conv_info.get("recrdUnit", ""),
                "conversation_theme": conv_info.get("convrsThema", ""),

                # 녹음자 정보
                "gender": speaker_info.get("gender", ""),
                "recorder_id": speaker_info.get("recorderId", ""),
                "age": speaker_info.get("age", ""),
            }

            row.update(text_feats)
            row.update(speech_feats)

            rows.append(row)

        except Exception as e:
            error_count += 1
            print("JSON 처리 실패:", json_path)
            print(e)

    df = pd.DataFrame(rows)

    print("처리 완료:", len(df))
    print("처리 실패:", error_count)
    print("WAV 매칭 성공:", matched_count)
    print("WAV 매칭 실패:", not_matched_count)

    return df


def print_summary(df):
    """
    생성된 노인 자유대화 특징 데이터의 기본 통계를 출력한다.
    """
    if len(df) == 0:
        print("생성된 데이터가 없습니다.")
        return

    print("\n매칭 결과")
    print(df["matched"].value_counts())

    print("\n성별 분포")
    print(df["gender"].value_counts(dropna=False))

    print("\n나이 분포 상위 20개")
    print(df["age"].value_counts(dropna=False).head(20))

    print("\n기본 통계")

    stat_cols = [
        "record_time_float",
        "word_count",
        "char_count",
        "unique_word_count",
        "lexical_diversity",
        "repetition_ratio",
        "speech_rate_word",
        "speech_rate_char",
        "short_answer_flag",
        "slow_speech_flag",
        "long_recording_flag",
        "low_content_slow_speech_flag",
    ]

    existing_cols = [col for col in stat_cols if col in df.columns]

    print(df[existing_cols].describe())


def main():
    print("노인 자유대화 챗봇 feature 생성 시작")

    df = load_elderly_json_files(
        ELDERLY_LABEL_ROOT,
        ELDERLY_AUDIO_ROOTS
    )

    print("feature 생성 완료:", len(df))

    df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")

    print("저장 완료:", OUTPUT_CSV)

    print_summary(df)


if __name__ == "__main__":
    main()