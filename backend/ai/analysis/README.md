# MIDAS AI 음성 분석 모듈

이 모듈은 Android 기반 인지기능 저하 위험 선별 애플리케이션에서 사용할 수 있는 음성 기반 발화 이상 점수화 prototype입니다.

## 현재 Prototype

현재 확보한 구음장애 데이터셋에서는 확인된 정상 대조군 데이터가 없기 때문에, 본 prototype은 정상 발화와 구음장애 발화를 직접 분류하지 않습니다.

대신 `25.언어+뇌신경장애` 데이터를 발화 이상 참고군으로 사용합니다. 음성 파일에서 MFCC, RMS, Zero Crossing Rate, Spectral Centroid 등의 음향 특징을 추출하고, 사용자의 음성을 참고군 프로필과 비교하여 유사도 기반 발화 이상 점수를 계산합니다.

음성 품질 지표 보강을 위해 openSMILE eGeMAPS를 사용하여 F0, jitter, shimmer, HNR, voiced/unvoiced segment 계열 feature를 추가 추출합니다. openSMILE은 음성 분석 필수 의존성이므로 `requirements.txt`로 함께 설치해야 합니다. 런타임에서 eGeMAPS 추출에 실패하면 `egemaps_available=false`와 오류 메시지가 결과에 포함됩니다.

## 주요 출력값

- `dysarthria_similarity_score`: 발화 이상 참고군과의 음향적 유사도 점수
- `distance_from_reference`: 참고군 프로필과 사용자 음성 사이의 거리값
- `speech_abnormality_level`: 발화 이상 수준
- `speech_abnormality_score`: 최종 위험도 산출에 반영할 발화 이상 점수
- `egemaps_available`: openSMILE eGeMAPS feature 추출 성공 여부
- `f0_semitone_mean`: eGeMAPS 기반 평균 F0
- `jitter_local`: 주기별 pitch 변동성 지표
- `shimmer_local_db`: 음성 진폭 변동성 지표
- `hnr_db`: harmonic-to-noise ratio 계열 음성 품질 지표
- `voice_break_count`, `voice_break_ratio`: 발화 중단/무성 구간 보조 지표

## 데이터셋 관련 안내

AI-Hub 원천 데이터, 라벨링 JSON 파일, WAV 음성 파일, 데이터셋에서 파생된 CSV 파일은 라이선스 및 개인정보 보호 문제로 인해 본 저장소에 포함하지 않습니다.

데이터셋은 사용자가 AI-Hub에서 직접 다운로드한 뒤 `data/raw/` 폴더에 배치해야 합니다.

## Isolation Forest 이상치 모델 학습

`src/train_speech_anomaly_iforest.py`는 일반 고령자 자유대화 음성 feature를 정상 기준으로 학습하고,
구음장애 8초 segment feature를 외부 비교셋으로 사용해 발화 이상 점수 분포를 검증합니다.
기본 실행 시 핵심 음성 품질 feature, MFCC 포함 feature, 전체 공통 숫자 feature를 모두 비교한 뒤
정상군 오탐률과 구음장애군 분리도가 가장 좋은 feature 구성을 선택합니다.

기본 입력 파일은 `MIDAS_EXTRACTED_DIR` 환경변수가 있으면 해당 폴더를 사용하고,
없으면 `backend/ai/analysis/outputs` 아래 CSV를 찾습니다.

```bash
python backend/ai/analysis/src/train_speech_anomaly_iforest.py
```

필요하면 CSV 경로를 직접 지정할 수 있습니다.

```bash
python backend/ai/analysis/src/train_speech_anomaly_iforest.py \
  --normal-csv path/to/elderly_chatbot_reference_features_egemaps_sample_1000.csv \
  --abnormal-csv path/to/dysarthria_neuro_25_segment_features_egemaps.csv
```

실행 결과는 `backend/ai/analysis/model/speech_anomaly_iforest_v2.pkl`과
`backend/ai/analysis/model/speech_anomaly_iforest_v2_report.json`에 저장됩니다.
모델 산출물과 데이터 CSV는 로컬 실행 결과물이므로 git에 올리지 않습니다.

음성 파일 1개의 분석 결과는 아래처럼 확인할 수 있습니다.

```bash
python backend/ai/analysis/src/test_speech_anomaly_audio.py path/to/test_audio.mp3
```

## 데모 실행 방법

```bash
pip install -r requirements.txt
python src/demo_run.py
