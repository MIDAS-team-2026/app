from pathlib import Path
import os
import json
import pandas as pd
from tqdm import tqdm

from audio_features import extract_audio_features


# =========================
# 실제 데이터 경로 설정
# =========================

TL_ROOT = Path(r"D:\013.구음장애 음성인식 데이터\01.데이터\1.Training\라벨링데이터\TL01_뇌신경장애")
TS_ROOT = Path(r"D:\013.구음장애 음성인식 데이터\01.데이터\1.Training\원천데이터\TS01_뇌신경장애")

OUTPUT_DIR = Path(r"D:\MIDAS_EXTRACTED")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# 전체 음성 기반 reference feature 파일입니다.
OUTPUT_CSV = OUTPUT_DIR / "dysarthria_25_reference_features.csv"


def load_tl_metadata(tl_root):
    """
    TL 라벨링 JSON 파일을 읽어 메타데이터 DataFrame으로 변환합니다.
    """

    rows = []
    json_files = []

    for root, dirs, files in os.walk(tl_root):
        for file in files:
            if file.lower().endswith(".json"):
                json_files.append(Path(root) / file)

    print("총 JSON 파일 수:", len(json_files))

    for json_path in json_files:
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        disease_info = data.get("Disease_info", {})
        meta_info = data.get("Meta_info", {})
        patient_info = data.get("Patient_info", {})
        test_info = data.get("Test_info", {})

        rows.append({
            "json_path": str(json_path),
            "folder_name": json_path.parent.name,
            "json_file_name": json_path.name,
            "audio_file_name": data.get("File_id", ""),
            "play_time": data.get("playTime", ""),
            "file_size": data.get("FileSize", ""),

            "disease_type": disease_info.get("Type", ""),
            "subcategory1": disease_info.get("Subcategory1", ""),
            "subcategory2": disease_info.get("Subcategory2", ""),
            "subcategory3": disease_info.get("Subcategory3", ""),
            "subcategory6": disease_info.get("Subcategory6", ""),

            "language": meta_info.get("Language", ""),
            "sampling_rate": meta_info.get("SamplingRate", ""),
            "recording_environment": meta_info.get("RecordingEnviron", ""),
            "recording_device": meta_info.get("RecordingDevice", ""),
            "file_format": meta_info.get("FileFormat", ""),

            "sex": patient_info.get("Sex", ""),
            "age": patient_info.get("Age", ""),
            "area": patient_info.get("Area", ""),

            "test_method": test_info.get("TestMethod", "")
        })

    return pd.DataFrame(rows)


def collect_wav_files(ts_root):
    """
    TS 원천데이터 폴더에서 wav 파일 목록을 수집합니다.
    파일명 기준으로 JSON의 File_id와 매칭하기 위해 dict 형태로 반환합니다.
    """

    wav_files = []

    for root, dirs, files in os.walk(ts_root):
        for file in files:
            if file.lower().endswith(".wav"):
                wav_files.append(Path(root) / file)

    print("총 WAV 파일 수:", len(wav_files))

    return {p.name: p for p in wav_files}


def prepare_25_dataset(tl_df, wav_dict):
    """
    25.언어+뇌신경장애 데이터만 필터링하고,
    File_id 기준으로 실제 wav 파일 경로를 매칭합니다.
    """

    tl_25_df = tl_df[tl_df["folder_name"].str.contains("25.언어", regex=False)].copy()

    tl_25_df["audio_path"] = tl_25_df["audio_file_name"].map(
        lambda x: str(wav_dict[x]) if x in wav_dict else "NOT_FOUND"
    )

    matched_25_df = tl_25_df[tl_25_df["audio_path"] != "NOT_FOUND"].copy()

    print("25번 전체:", len(tl_25_df))
    print("25번 WAV 매칭 성공:", len(matched_25_df))
    print("25번 WAV 매칭 실패:", (tl_25_df["audio_path"] == "NOT_FOUND").sum())

    return matched_25_df


def extract_reference_features(matched_df):
    """
    매칭된 25.언어+뇌신경장애 wav 파일 전체 구간에서 음향 특징을 추출합니다.
    """

    feature_rows = []

    for idx, row in tqdm(matched_df.iterrows(), total=len(matched_df)):
        features = extract_audio_features(row["audio_path"])

        if features is None:
            continue

        combined = row.to_dict()
        combined.update(features)
        combined["reference_group"] = "language_neuro_25"
        combined["feature_extract_range"] = "full_audio"

        feature_rows.append(combined)

    feature_df = pd.DataFrame(feature_rows)

    print("특징 추출 완료:", len(feature_df))

    return feature_df


def main():
    print("TL 메타데이터 읽는 중...")
    tl_df = load_tl_metadata(TL_ROOT)

    print("WAV 파일 목록 수집 중...")
    wav_dict = collect_wav_files(TS_ROOT)

    print("25.언어+뇌신경장애 데이터 준비 중...")
    matched_25_df = prepare_25_dataset(tl_df, wav_dict)

    print("전체 음성 기반 음향 특징 추출 중...")
    feature_df = extract_reference_features(matched_25_df)

    feature_df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")

    print("저장 완료:", OUTPUT_CSV)


if __name__ == "__main__":
    main()