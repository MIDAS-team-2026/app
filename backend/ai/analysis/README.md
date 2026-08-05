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

## 데모 실행 방법

```bash
pip install -r requirements.txt
python src/demo_run.py
