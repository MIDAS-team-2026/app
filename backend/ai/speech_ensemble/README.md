# 음성 특징 기반 앙상블

40개 음향 특징으로 자유대화/구음장애 데이터 출처를 구분하는 실험 코드입니다. 기존 학습 실험에서 실행한 전처리·분류·확률 보정을 공개용 경로 인자로 정리했습니다. D드라이브 앱의 음성 분석 함수와 연결되어 있으며 최종 음성 예측은 항상 앙상블입니다.

## GitHub에 올릴 파일

이 폴더의 Python 코드(`train.py`, `predict.py`, `runtime.py`, `audio_features.py`, `test_integration.py`, `__init__.py`, `estimators/*.py`), `features.json`, `requirements.txt`, `requirements-tested.txt`, `benchmark_metrics.csv`, `README.md`, `.gitignore`를 올립니다. 앱 연결 변경 파일 `../main.py`와 `../analysis/src/user_turn_analysis.py`도 함께 커밋합니다. `benchmark_metrics.csv`는 파일 ID와 개인 정보 없는 집계 지표입니다.

원본 데이터와 녹음, 개인 특징 CSV, 파일별 예측/분할 목록, 모델 가중치, 환경 폴더는 포함하지 않습니다. 저장 가중치는 로컬에서 사용하고, 외부 배포 여부는 사용 데이터 및 의존성의 이용조건에 맞게 별도 결정합니다. 이 패키지에는 임의의 재배포 라이선스를 부여하지 않았습니다. openSMILE을 비롯한 의존성의 사용 조건은 각각 적용됩니다.

## 모델과 점수

### 코드 출처

알고리즘 구현은 scikit-learn의 `RandomForestClassifier`, `LogisticRegression`, `SVC`와 CatBoost의 `CatBoostClassifier`를 사용합니다. 학습된 파라미터는 로컬 음향 특징 CSV로 학습한 결과이며, 외부 사전학습 가중치를 사용하지 않았습니다.

`train.py`는 기존 프로젝트의 CSV 모델 비교 실험을 확장하여 전처리·학습·확률 보정·앙상블 평가를 구성한 코드입니다. `audio_features.py`는 기존 프로젝트에서 CSV 생성에 사용한 librosa/openSMILE 추출기를 포함한 것입니다. `predict.py`는 이 모델과 추출기를 연결하는 파일 입력 CLI입니다.

- RF: 기존 저장 RF 설정인 200 trees, max_depth=None, min_samples_leaf=1, max_features=sqrt, seed=42를 재사용합니다. 클래스 가중치는 기존 beta=0.99 방식으로 현재 fit 라벨에서 다시 계산합니다. 15개 특징용 가중치를 확장한 것이 아니라 40개 특징으로 새로 학습한 RF입니다. 공통 화자/클래스 sample_weight도 적용합니다.
- Logistic Regression: C=0.1/1.
- SVM: RBF kernel, C=1/10.
- CatBoost: 400 iterations, depth=4/6, learning_rate=0.05.
- 모델별 검증 Macro-F1로 설정 선택, 별도 calibration에서 sigmoid 확률 보정.
- 앙상블은 보정된 네 확률 각각 25% 평균. 검증 최고 단일 모델은 비교용으로만 기록하며 앱과 CLI 모두 동일 가중 앙상블을 출력합니다.
- 점수=확률×100, 단계 경계=20/40/60/80. 장애 중증도나 치매 진단 점수가 아닙니다.

특징은 MFCC 26개, 에너지/스펙트럼 통계 5개, 발성/쉼/HNR 9개입니다. 정확한 이름과 순서는 [features.json](features.json)에 있습니다. `voice_break_ratio`는 이 추출기에서 `1 - voice_activity_ratio`로 계산되므로 독립적인 발성 장애 검출값이 아닙니다.

## 입력 데이터 계약

CSV는 사용자가 로컬에 준비합니다. `data/`는 Git에서 제외됩니다.

| 입력 | 필수 열 |
|---|---|
| 자유대화 CSV | audio_path, recorder_id, features.json의 40개 특징 |
| 구음장애 CSV | segment_id, speaker_key, 같은 40개 특징 |
| 분할 manifest CSV | sample_id, group, label, split |

label은 자유대화=0, 구음장애=1입니다. 자유대화 sample_id는 audio_path, group은 `elderly:`+recorder_id입니다. 구음장애 sample_id는 segment_id, group은 `dysarthria:`+speaker_key입니다. split은 fit/calibration/validation/test 중 하나입니다. 같은 화자를 한 split에만 배정하고 각 split에 두 클래스를 포함해야 합니다. manifest 생성/원본 라벨 파싱은 이 패키지에 포함하지 않았습니다.

기존 실험은 자유대화 30,000개와 구음장애 30,753개를 사용했습니다. fit 28,014, calibration 6,864, validation 14,785, test 11,090개입니다. 자유대화 원본 전체 구간 및 구음장애 기존 세그먼트를 사용했습니다. 전처리 중앙값 대체/표준화는 fit에서만 적합합니다.

## 설치와 실행

Python 가상환경과 PATH에 등록된 FFmpeg가 필요합니다. 의존성은 최소 목록이며, 새 환경에서 버전을 고정한 설치 검증은 아직 수행하지 않았습니다. 저장된 scikit-learn 모델은 학습 환경과 같은 버전에서 로딩하는 것을 권장하며 신뢰하는 joblib 파일만 사용합니다.

```powershell
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/python train.py --elderly-csv data/elderly.csv --dysarthria-csv data/dysarthria.csv --manifest data/split_manifest.csv --output outputs
.venv/Scripts/python predict.py --model outputs/ensemble.joblib --audio data/sample.m4a --output outputs/prediction.json
```

명령은 이 폴더에서 실행합니다. 기존 학습 가중치를 로컬 `models/ensemble.joblib`에 놓았다면 `--model models/ensemble.joblib`로 지정할 수 있습니다. 가중치를 다운로드하는 공개 URL은 제공하지 않습니다.

M4A 등 입력은 임시 PCM16 WAV로 변환합니다. 길이 자르기·잡음 제거·음량 정규화·재샘플링 없이 원본 길이/주파수를 유지하며 원본을 수정하지 않습니다. 임시 WAV는 예측 후 정리됩니다. 추출기는 60초 블록 통계 및 eGeMAPS를 계산합니다.

## 평가와 한계

### 동일 가중 앙상블의 테스트 점수 분포

| 데이터 출처 | 개수 | 평균 점수 | 1단계 | 2단계 | 3단계 | 4단계 | 5단계 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 노인 자유대화 | 6,314 | 0.086 | 6,303 | 7 | 4 | 0 | 0 |
| 구음장애 | 4,776 | 99.938 | 0 | 0 | 0 | 2 | 4,774 |

이는 RF 설정 변경 전 40개 특징·각 25% 가중치 구성의 결과입니다. 현재 재학습 결과는 아래에 별도 기록합니다. 34개 특징 제외 실험, 가중치 최적화, 개인 백분위 변환은 이 제출용 구성에 적용하지 않았습니다. 별도의 인지장애 진단 테스트셋은 포함하지 않습니다.

[benchmark_metrics.csv](benchmark_metrics.csv)는 기존 실행의 집계 결과입니다. 샘플 단위 비가중 지표이며 모델 선택에 사용한 화자/클래스 가중 지표와 다릅니다. CSV의 non_test는 fit+calibration+validation입니다.

기존 테스트에서 동일 가중 앙상블은 11,090개 중 1개를 오분류했습니다. 하지만 두 데이터 출처의 녹음 조건 차이가 남아 있고 개인 녹음 45개에서는 고득점 쏠림이 확인되었습니다. 임상 진단 성능으로 해석할 수 없습니다. D드라이브 Python 3.14.3/scikit-learn 1.8.0에서 재학습하고 저장 모델 재로딩 및 앱 분석 함수를 검증했습니다. 45개 기존 특징의 RF/앙상블 비교와 test1/test5 원본 M4A 재추출 예측 일치를 확인했습니다. 새 컴퓨터 설치 및 실제 Spring/DB 저장 검증은 별도입니다.

FDA-2의 5수준 평가 접근을 참고하되 20점 간격은 자체 표시 규칙입니다. 관련 연구: [FDA-2 검증](https://pmc.ncbi.nlm.nih.gov/articles/PMC9980487/), [음향/발성 특징 기반 분류](https://www.isca-archive.org/interspeech_2018/np18_interspeech.html). 이들 연구가 현재 점수 경계나 앙상블 가중치를 검증한 것은 아닙니다.

## D드라이브 앱 연결

최종 출력은 `ensemble40`으로 고정합니다. `MIDAS_SPEECH_MODEL` 선택 설정은 제거했습니다. 모델 기본 위치는 `speech_ensemble/models/ensemble.joblib`이며 `MIDAS_ENSEMBLE_MODEL_PATH`로 위치를 변경할 수 있습니다. 기존 15개 특징 RF 파일은 비교 자료로 보존하며 서비스 추론에 사용하지 않습니다.

`runtime.py`가 모델을 캐시하고 전처리·예측을 수행합니다. 기존 통합 건강 점수의 계산 방식은 유지하고, CLI/API 응답에 `speechReferenceScore`·`speechReferenceBand`·`speechModelScores`를 별도 추가합니다. 이 필드는 기존 Java DTO/DB에 자동 저장되지는 않습니다. UI/DB 연결은 별도 작업입니다. 분석 실패 시 참고 점수/단계는 null입니다.



### 모델별 파일 구성

| 코드 | 역할 | 저장 파일 |
|---|---|---|
| estimators/random_forest.py | 기존 RF 설정으로 40개 특징 학습 | models/random_forest.joblib |
| estimators/logistic_regression.py | 로지스틱 회귀 후보 | models/logistic_regression.joblib |
| estimators/svm.py | RBF SVM 후보 | models/svm.joblib |
| estimators/catboost_model.py | CatBoost 후보 | models/catboost.joblib |
| estimators/__init__.py | 모델 등록 및 파일명 | — |
| train.py | 공통 전처리, 화자 분할 검사, 학습·보정·평가 | models/ensemble.joblib |
| runtime.py | 네 모델 로딩, 확률 각 25% 합산, 점수·단계 출력 | — |

각 모델 파일에는 분류기와 해당 확률 보정기를 함께 저장합니다. ensemble.joblib에는 공통 전처리, 특징 순서, 구성 파일 목록과 메타데이터를 저장합니다. 배포 시 다섯 joblib 파일을 같은 폴더에 놓아야 합니다. 모델을 찾지 못하면 분석 불가를 반환하며 RF 단독 결과로 대체하지 않습니다. 모델 가중치와 원본 데이터는 Git에서 제외됩니다.

### 현재 재학습 및 연결 테스트

기존 화자 분할을 유지하여 네 모델 모두 40개 특징으로 재학습했습니다. RF의 기존 학습 설정을 재사용하며 전처리와 가중치는 새 fit 데이터에 적합합니다. 모델 선택·보정에 test를 사용하지 않았지만 이전 개발에서 이미 확인한 테스트셋의 재평가이므로 새 독립 외부 검증은 아닙니다.

| 데이터 출처 | 개수 | 평균 점수 | 1단계 | 2단계 | 3단계 | 4단계 | 5단계 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 노인 자유대화 | 6314 | 0.088 | 6303 | 7 | 4 | 0 | 0 |
| 구음장애 | 4776 | 99.939 | 0 | 0 | 0 | 2 | 4774 |

앙상블 화자/클래스 가중 Macro-F1=0.999921, Brier=0.000170. 단계 분포는 RF 변경 전과 같습니다. 학습 후보 평가 시간 합계는 16.7초이며 CSV 읽기 및 결과 저장 시간은 제외합니다.

개인 녹음 45개 예측, 원본 test1/test5 M4A 재추출 일치, 구 RF 선택 환경변수를 넣어도 앙상블 출력, 무음·짧은 음성·없는 파일 처리, 단계 경계, API 핸들러 응답을 확인했습니다. Spring 전송은 mock 처리했으며 실제 HTTP/DB/UI 검증은 포함하지 않습니다.

- test1.m4a: 현재 앙상블 97.89점, 5단계.
- test5.m4a: 현재 앙상블 76.92점, 4단계.

개인 녹음은 정답 라벨이 없어 점수 변화만으로 성능 향상을 판단하지 않습니다. 기존 benchmark_metrics.csv는 앞선 실험 결과이며 현재 지표는 로컬 models/metrics.csv에 있습니다. 기존 종합 건강점수 계산은 유지되고 앙상블 참고 점수·단계는 referenceAnalysis로 반환됩니다. Java 저장 DTO/DB/UI 반영은 별도입니다. 서버를 재시작해야 새 모델이 로딩됩니다.
