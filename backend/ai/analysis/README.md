# MIDAS AI 음성 분석 모듈

이 모듈은 Android 기반 인지기능 저하 위험 선별 애플리케이션에서 사용할 수 있는 음성 기반 발화 이상 점수화 prototype입니다.

## 현재 Prototype

현재 확보한 구음장애 데이터셋에서는 확인된 정상 대조군 데이터가 없기 때문에, 본 prototype은 정상 발화와 구음장애 발화를 직접 분류하지 않습니다.

대신 `25.언어+뇌신경장애` 데이터를 발화 이상 참고군으로 사용합니다. 음성 파일에서 MFCC, RMS, Zero Crossing Rate, Spectral Centroid 등의 음향 특징을 추출하고, 사용자의 음성을 참고군 프로필과 비교하여 유사도 기반 발화 이상 점수를 계산합니다.

## 주요 출력값

- `dysarthria_similarity_score`: 발화 이상 참고군과의 음향적 유사도 점수
- `distance_from_reference`: 참고군 프로필과 사용자 음성 사이의 거리값
- `speech_abnormality_level`: 발화 이상 수준
- `speech_abnormality_score`: 최종 위험도 산출에 반영할 발화 이상 점수

## 데이터셋 관련 안내

AI-Hub 원천 데이터, 라벨링 JSON 파일, WAV 음성 파일, 데이터셋에서 파생된 CSV 파일은 라이선스 및 개인정보 보호 문제로 인해 본 저장소에 포함하지 않습니다.

데이터셋은 사용자가 AI-Hub에서 직접 다운로드한 뒤 `data/raw/` 폴더에 배치해야 합니다.

## 데모 실행 방법

```bash
pip install -r requirements.txt
python src/demo_run.py