# DSD 문서

![커버 이미지](./images/dsd/cover.png)

영남대학교 컴퓨터공학과

| 학번 | 이름 |
|------|------|
| 22210599 | 김민정 |
| 22210596 | 전선영 |
| 22213490 | 이주연 |
| 22112045 | 김형섭 |
| 22012158 | 김경빈 |
| 22113640 | 김동현 |

---

## 목차

1. [시스템 개요](#1-시스템-개요)
   - 1.1 [프로젝트의 개요](#11-프로젝트의-개요)
   - 1.2 블록 다이어그램
2. [데이타 베이스 설계](#2-데이타-베이스-설계)
   - 2.1 Schema 및 ER 다이아그램
   - 2.2 각 테이블의 자료구조
  
3. [AI 분석 모듈 설계](#3-AI-분석-모듈-설계)
   - 3.1 음성 특징 분석 모듈
   - 3.2 회상 일치도 분석 모듈
   - 3.3 종합 위험도 계산 모듈
   - 3.4 AI 분석 모듈 확장 방향

---

## 1. 시스템 개요

### 1.1 프로젝트의 개요

본 프로젝트는 사용자와 AI 간의 일상 음성 대화를 매일 루틴으로 수행하고, 음성 데이터의 음향적·언어적 특징 분석과 저장된 기록 기반의 기억 일치도 평가를 통해 인지기능 저하 고위험 가능성을 조기에 선별하는 안드로이드 애플리케이션이다.

앱은 사용자와 보호자 두 가지 역할로 분리 운영되며, 회원가입 시 역할을 선택하고 사용자-보호자 간 연결 코드를 통해 계정을 연동한다. 사용자는 매일 음성 대화 루틴을 수행하고 분석 결과를 확인하며, 보호자는 사용자의 분석 결과 열람, 위치 정보 조회, 이상 징후 알람 수신 등의 기능을 제공받는다.

앱의 전체적인 흐름은 다음과 같다.

![앱 Flow Chart](./images/dsd/flowchart.png)

**[그림] 앱 Flow Chart**

### 1.2 블록 다이어그램
 
전체 시스템의 구조를 한눈에 볼 수 있도록 아래 그림2에서 블록 다이어그램으로 정리하였다. 이 시스템은 크게 사용자, 안드로이드 앱, 백엔드 서버, 보호자 이 네 가지 영역으로 구성되어 있다.
 
우선 사용자 영역은 시스템의 출발점 역할을 한다. 사용자는 음성 입력이나 터치를 이용해 앱과 직접 상호작용하며, 회원가입이나 로그인, 회상 과제 답변, 설정 변경 등 다양한 기능을 사용할 수 있다. 사용자가 입력하는 모든 정보는 안드로이드 앱으로 전달된다.
 
앱 영역은 시스템의 핵심 부분이다. 전체적으로 UI 레이어와 기능별 모듈로 나눌 수 있는데, UI 레이어는 온보딩, 홈, 설정, 결과 같은 주요 화면을 담당한다. 기능 모듈에서는 음성 대화(음성 인식 및 키워드 추출), 회상 과제 자동 생성과 채점, 분석 결과 시각화, GPS 위치 추적과 이동 경로 파악, 알림 시스템, 회원가입 코드 방식 연결, 그리고 글자 크기·말하기 속도·알림 시간 같은 사용자 맞춤 설정을 관리한다. 로컬 데이터 저장에는 SharedPreferences, MySQL, Cache를 활용하고, 서버와의 통신은 REST API, WebSocket, FCM 푸시 기능을 이용한다. 또한 Google STT·TTS, Google Maps SDK, 네이버 클로바 AI, Firebase Auth처럼 다양한 외부 SDK도 함께 사용한다.
 
백엔드 서버 영역은 클라우드나 온프레미스 환경에 구축되며, 인증 서버, AI 분석 엔진, 음성 처리 API, GPS 데이터 처리, 알림 발송 서비스, 사용자 DB, 분석 결과 DB 등으로 구성된다. 앱으로부터 음성 데이터와 인지 과제의 결과를 받아 분석한 뒤, 다시 앱이나 보호자에게 결과를 전달하는 역할을 맡는다.
 
보호자 영역에서는 서버로부터 분석된 데이터를 받아 보호자에게 보여준다. 여기에는 보호자 홈, 분석 리포트, GPS 위치 확인, 이상 징후 알림, 사용자 코드 연동 기능 등이 포함된다. 사용자 코드로 어르신과 보호자가 자동으로 연결되는 것이 특징이다.
 
앱과 서버 간 통신은 REST API와 WebSocket을 이용해서 이루어지며, 서버에서 앱으로 가는 응답은 점선으로 표시해 구분하였다. 서버와 보호자 앱 사이의 데이터는 푸시 알림과 API를 통해 양방향으로 오가도록 설계하였다.
 
<img width="1454" height="824" alt="전체 블록 다이어그램" src="https://github.com/user-attachments/assets/3a23b741-01b1-4f73-a2ef-11351c968b52" />

**[그림 2] 전체 블록 다이어그램**

---

## 3. AI 분석 모듈 설계

### 3.1 음성 특징 분석 모듈

#### 3.1.1 모듈 개요

음성 특징 분석 모듈은 사용자가 Android App에서 녹음한 음성 데이터를 기반으로 발화 이상 징후를 분석하는 소프트웨어 모듈이다. 본 프로젝트는 별도의 하드웨어 장치를 사용하지 않으므로, 음성 데이터는 스마트폰 마이크를 통해 수집되며 서버에 저장된 음성 파일과 STT 변환 텍스트를 분석 대상으로 사용한다.

본 모듈은 사용자의 음성에서 음성 길이, 음성 에너지, 무성음/잡음 비율, 음향적 변동성 등의 특징을 추출하고, 구음장애 또는 발화 이상 참고군과의 음향적 유사도를 기반으로 음성 이상 점수를 계산한다.

현재 단계에서는 인지기능장애 전용 모델을 직접 학습하기보다는, 구음장애/발화 이상 참고군의 음향 특징을 기준 프로파일로 생성한 뒤 사용자 음성과의 거리 및 유사도를 계산하는 방식으로 구현한다. 이후 인지기능장애 음성 데이터셋이 확보되면 정상군, 경도인지장애, 치매 위험군 분류 모델로 확장할 수 있도록 설계한다.

본 모듈에서 계산된 음성 기반 점수는 종합 위험도 계산 모듈에서 `speech_score`로 사용된다.

#### 3.1.2 모듈 기능

| 구분 | 내용 |
|---|---|
| 모듈명 | Speech Feature Analysis Module |
| 주요 기능 | 사용자 음성에서 음향 특징을 추출하고 발화 이상 점수 계산 |
| 입력 데이터 | 음성 파일 경로, 음성 길이, STT 변환 텍스트 |
| 처리 방식 | 음향 특징 추출, 참고군 프로파일과의 거리 계산, 유사도 기반 점수화 |
| 출력 데이터 | dysarthria_similarity_score, distance_from_reference, speech_abnormality_score, speech_abnormality_level |
| 연동 테이블 | audio_records, risk_analysis_results |


#### 3.1.3 분석 대상 특징

음성 특징 분석 모듈에서 사용하는 주요 특징은 다음과 같다.

| 특징명 | 설명 | 활용 목적 |
|---|---|---|
| audio_duration | 음성 파일 전체 길이 | 발화 지속 시간 확인 |
| rms_mean | 음성 에너지 평균값 | 발화 강도 및 음성 크기 분석 |
| rms_std | 음성 에너지 표준편차 | 발화 강도의 변동성 분석 |
| zcr_mean | Zero Crossing Rate 평균값 | 음성의 잡음성, 무성음 비율 분석 |
| zcr_std | Zero Crossing Rate 표준편차 | 발화 중 음향적 변화 정도 분석 |
| spectral_centroid_mean | 스펙트럼 중심 평균값 | 음색 및 주파수 중심 분석 |
| spectral_centroid_std | 스펙트럼 중심 표준편차 | 주파수 변화의 불안정성 분석 |
| mfcc_mean | MFCC 평균값 | 음성의 음색적 특징 분석 |
| mfcc_std | MFCC 표준편차 | 발음 및 음성 패턴의 변동성 분석 |
| speech_rate_word | 초당 단어 수 | 발화 속도 분석 |
| repetition_ratio | 반복 표현 비율 | 반복 발화 여부 분석 |
| lexical_diversity | 어휘 다양도 | 언어 사용 다양성 분석 |

음향 특징 중 `rms`, `zcr`, `spectral_centroid`, `mfcc`는 음성 파일 자체에서 추출하고, `speech_rate_word`, `repetition_ratio`, `lexical_diversity`는 STT 변환 텍스트와 음성 길이를 함께 이용하여 계산한다.

#### 3.1.4 내부 동작 흐름

음성 특징 분석 모듈의 내부 동작 흐름은 다음과 같다.

```text
[1] 사용자 음성 녹음
        ↓
[2] audio_records 테이블에 음성 파일 경로 및 기본 정보 저장
        ↓
[3] STT를 통해 음성을 텍스트로 변환
        ↓
[4] transcript_text 저장
        ↓
[5] 음성 파일 로드
        ↓
[6] RMS, ZCR, Spectral Centroid, MFCC 등 음향 특징 추출
        ↓
[7] STT 텍스트 기반 발화 속도, 반복 비율, 어휘 다양도 계산
        ↓
[8] 구음장애/발화 이상 참고군 프로파일과 거리 계산
        ↓
[9] dysarthria_similarity_score 계산
        ↓
[10] speech_abnormality_score 및 speech_abnormality_level 산출
        ↓
[11] 종합 위험도 계산 모듈로 speech_score 전달

```


#### 3.1.5 Input / Process / Output

##### Input

| 입력값 | 설명 |
|---|---|
| record_id | 분석 대상 음성 기록 ID |
| user_id | 사용자 ID |
| session_id | 대화 세션 ID |
| audio_file_path | 서버에 저장된 음성 파일 경로 |
| audio_duration | 음성 파일 길이 |
| transcript_text | STT를 통해 변환된 사용자 발화 텍스트 |
| recorded_at | 녹음 생성 시각 |

##### Process

1. `record_id`를 기준으로 `audio_records` 테이블에서 음성 파일 경로와 STT 텍스트를 조회한다.
2. 음성 파일을 로드하여 샘플링 레이트와 전체 길이를 확인한다.
3. 음성 파일에서 RMS, ZCR, Spectral Centroid, MFCC 등의 음향 특징을 추출한다.
4. STT 텍스트에서 단어 수, 글자 수, 어휘 다양도, 반복 표현 비율을 계산한다.
5. 음성 길이와 단어 수를 이용하여 발화 속도를 계산한다.
6. 사전에 생성한 구음장애/발화 이상 참고군 프로파일과 사용자 음성 특징 벡터 간의 거리를 계산한다.
7. 거리값을 기반으로 `dysarthria_similarity_score`를 계산한다.
8. 유사도 점수를 기준으로 `speech_abnormality_score`를 0~10점 범위로 변환한다.
9. 최종 점수에 따라 `Low`, `Medium`, `High` 단계로 분류한다.
10. 계산된 음성 점수는 종합 위험도 계산 모듈의 `speech_score`로 전달한다.

##### Output

| 출력값 | 설명 |
|---|---|
| dysarthria_similarity_score | 구음장애/발화 이상 참고군과의 음향적 유사도 |
| distance_from_reference | 참고군 프로파일과 사용자 음성 특징 간 거리 |
| speech_abnormality_score | 음성 이상 징후 점수 |
| speech_abnormality_level | 음성 이상 단계 |
| speech_score | 종합 위험도 계산에 사용되는 음성 기반 점수 |
| analyzed_at | 분석 완료 시각 |
---

### 3.2 회상 일치도 분석 모듈

#### 3.2.1 모듈 개요

회상 일치도 분석 모듈은 사용자의 초기 답변 데이터와 이후 회상 답변 데이터를 비교하여 기억 유지 정도를 점수화하는 소프트웨어 모듈이다.

본 모듈은 사용자의 일반 자유 발화에서 과거 기억을 임의로 추출하는 방식이 아니라, 사전에 정의된 회상 질문을 기반으로 초기 기준 답변을 저장하고, 이후 동일하거나 유사한 질문을 다시 제시하여 현재 답변과 비교하는 방식으로 동작한다.

사용자의 음성 답변은 STT(Speech To Text) 처리를 통해 텍스트로 변환되며, 변환된 텍스트는 `audio_records.transcript_text`에 저장된다. 이후 Python AI 서버는 과거 답변과 현재 답변의 의미 유사도, 핵심 키워드 일치도, 질문 유형별 가중치를 이용하여 최종 회상 일치도 점수인 `final_recall_score`를 계산한다.

계산된 회상 일치도 점수는 `recall_analysis_results` 테이블에 저장되며, 종합 위험도 계산 모듈에서 `recall_score`로 사용된다.

---

#### 3.2.2 모듈 기능

| 구분 | 내용 |
|---|---|
| 모듈명 | Recall Analysis Module |
| 주요 기능 | 과거 답변과 현재 답변을 비교하여 회상 일치도 점수 계산 |
| 입력 데이터 | 현재 답변 텍스트, 과거 기준 답변, 회상 질문 ID, 핵심 키워드 |
| 처리 방식 | 키워드 일치도 계산, 의미 유사도 계산, 질문 유형별 가중치 적용 |
| 출력 데이터 | similarity_score, keyword_score, final_recall_score |
| 저장 테이블 | recall_analysis_results |

---

#### 3.2.3 회상 질문 유형

회상 질문은 질문의 성격에 따라 FACT, PREFERENCE, MEMORY, DAILY 유형으로 구분한다. 질문 유형에 따라 의미 유사도와 키워드 일치도의 중요도가 다르기 때문에 서로 다른 가중치를 적용한다.

| 질문 유형 | 설명 | 예시 |
|---|---|---|
| FACT | 명확한 정답이 존재하는 사실 기반 질문 | 고향, 배우자 이름, 혈액형, 자녀 이름 |
| PREFERENCE | 사용자의 선호 정보를 확인하는 질문 | 좋아하는 음식, 좋아하는 계절, 좋아하는 가수 |
| MEMORY | 장기 기억 및 과거 경험을 확인하는 질문 | 첫 직장, 기억에 남는 여행, 과거 직업 경험 |
| DAILY | 단기 기억 및 시간 지남력을 확인하는 질문 | 오늘 날짜, 오늘 식사, 현재 계절 |

---

#### 3.2.4 내부 동작 흐름

회상 일치도 분석 모듈의 내부 동작 흐름은 다음과 같다.

```text
[1] 회상 질문 생성 또는 조회
        ↓
[2] 사용자 음성 답변 입력
        ↓
[3] STT를 통한 텍스트 변환
        ↓
[4] audio_records 테이블에 답변 텍스트 저장
        ↓
[5] 과거 기준 답변 조회
        ↓
[6] recall_keywords 기반 키워드 일치도 계산
        ↓
[7] Sentence-BERT 기반 의미 유사도 계산
        ↓
[8] 질문 유형별 가중치 적용
        ↓
[9] final_recall_score 계산
        ↓
[10] recall_analysis_results 테이블에 저장
        ↓
[11] 종합 위험도 계산 모듈로 전달
```

---

#### 3.2.5 Input / Process / Output

##### Input

| 입력값 | 설명 |
|---|---|
| user_id | 분석 대상 사용자 ID |
| recall_question_id | 회상 질문 ID |
| current_record_id | 현재 회상 답변 녹음 기록 ID |
| past_record_id | 비교 기준이 되는 과거 답변 녹음 기록 ID |
| transcript_text | STT를 통해 변환된 현재 답변 텍스트 |
| keyword_text | 회상 질문별 핵심 키워드 |
| question_type | FACT, PREFERENCE, MEMORY, DAILY 중 하나의 질문 유형 |

##### Process

1. `recall_question_id`를 기준으로 회상 질문 정보를 조회한다.
2. `current_record_id`를 기준으로 현재 답변 텍스트를 조회한다.
3. `past_record_id`를 기준으로 과거 기준 답변 텍스트를 조회한다.
4. `recall_keywords` 테이블에서 해당 질문의 핵심 키워드를 조회한다.
5. 현재 답변에 핵심 키워드가 포함되어 있는지 확인하여 `keyword_score`를 계산한다.
6. 과거 답변과 현재 답변을 Sentence-BERT 모델에 입력하여 의미 유사도인 `similarity_score`를 계산한다.
7. 질문 유형에 따라 의미 유사도와 키워드 일치도의 가중치를 다르게 적용한다.
8. 최종 회상 일치도 점수인 `final_recall_score`를 계산한다.
9. 분석 결과를 `recall_analysis_results` 테이블에 저장한다.
10. 최종 위험도 계산을 위해 `final_recall_score`를 종합 위험도 계산 모듈로 전달한다.

##### Output

| 출력값 | 설명 |
|---|---|
| similarity_score | 과거 답변과 현재 답변의 의미 유사도 점수 |
| keyword_score | 핵심 키워드 일치도 점수 |
| final_recall_score | 최종 회상 일치도 점수 |
| analyzed_at | 회상 분석 완료 시각 |

---

#### 3.2.6 회상 점수 계산 알고리즘

회상 일치도 점수는 의미 유사도 점수와 키워드 일치도 점수를 기반으로 계산한다.

의미 유사도는 과거 답변과 현재 답변의 문장 의미가 얼마나 유사한지를 나타내며, 키워드 일치도는 질문별 핵심 키워드가 현재 답변에 얼마나 포함되어 있는지를 나타낸다.

질문 유형에 따른 가중치는 다음과 같다.

| 질문 유형 | 의미 유사도 가중치 | 키워드 일치도 가중치 | 적용 이유 |
|---|---|---|---|
| FACT | 20% | 80% | 정답 키워드가 중요하기 때문 |
| PREFERENCE | 40% | 60% | 선호 정보는 키워드가 중요하지만 표현 차이도 고려해야 하기 때문 |
| MEMORY | 60% | 40% | 장기 기억 답변은 표현 방식이 다양할 수 있기 때문 |
| DAILY | 30% | 70% | 날짜, 식사, 계절 등 핵심 단어의 일치가 중요하기 때문 |

최종 회상 일치도 점수 계산식은 다음과 같다.

```text
final_recall_score =
(similarity_score × similarity_weight) +
(keyword_score × keyword_weight)
```

예를 들어 FACT 유형 질문의 경우 다음과 같이 계산한다.

```text
final_recall_score =
(similarity_score × 0.2) +
(keyword_score × 0.8)
```

---

#### 3.2.7 회상 점수 판정 기준

계산된 `final_recall_score`는 다음 기준에 따라 회상 상태를 분류한다.

| final_recall_score | 판정 단계 | 설명 |
|---|---|---|
| 80 이상 | 정상 | 과거 답변과 현재 답변의 일치도가 높은 상태 |
| 50 이상 80 미만 | 주의 | 일부 기억 차이 또는 답변 불일치가 존재하는 상태 |
| 50 미만 | 위험 | 과거 답변과 현재 답변의 차이가 큰 상태 |

해당 판정 결과는 단독 진단 결과가 아니라 앱 내부 위험도 계산에 활용되는 보조 지표로 사용한다.

---

#### 3.2.8 관련 데이터베이스 구조

##### recall_questions

회상 질문 정보를 저장하는 테이블이다. 사용자별로 제시되는 회상 질문의 내용, 질문 유형, 카테고리 정보를 관리한다.

| 속성 | 설명 |
|---|---|
| recall_question_id | 회상 질문 ID |
| user_id | 사용자 ID |
| question_text | AI가 사용자에게 제시한 질문 내용 |
| question_type | 질문 유형 |
| category | 질문 카테고리 |
| created_at | 질문 생성 시각 |

---

##### recall_keywords

회상 질문에 대한 핵심 키워드를 저장하는 테이블이다. 키워드 기반 일치도 계산 시 사용된다.

| 속성 | 설명 |
|---|---|
| keyword_id | 키워드 ID |
| recall_question_id | 회상 질문 ID |
| keyword_text | 핵심 키워드 |

---

##### audio_records

사용자의 음성 녹음 정보와 STT 변환 결과를 저장하는 테이블이다. 회상 분석에서는 과거 기준 답변과 현재 회상 답변을 모두 `audio_records`에 저장한 뒤 비교한다.

| 속성 | 설명 |
|---|---|
| record_id | 녹음 기록 ID |
| user_id | 사용자 ID |
| session_id | 대화 세션 ID |
| recall_question_id | 연결된 회상 질문 ID |
| parent_record_id | 비교 대상이 되는 과거 기준 답변 ID |
| answer_role | INITIAL 또는 RECALL |
| audio_file_path | 음성 파일 저장 경로 |
| transcript_text | STT 변환 결과 |
| recorded_at | 녹음 생성 시각 |

---

##### recall_analysis_results

과거 답변과 현재 답변을 비교한 회상 일치도 분석 결과를 저장하는 테이블이다.

| 속성 | 설명 |
|---|---|
| recall_result_id | 회상 분석 결과 ID |
| recall_question_id | 회상 질문 ID |
| past_record_id | 비교 기준이 되는 초기 답변 record_id |
| current_record_id | 현재 회상 답변 record_id |
| similarity_score | 의미 유사도 점수 |
| keyword_score | 핵심 키워드 일치도 점수 |
| final_recall_score | 최종 회상 일치도 점수 |
| analyzed_at | 분석 완료 시각 |

---

#### 3.2.9 회상 분석 API 인터페이스

회상 일치도 분석은 앱 또는 백엔드 서버에서 AI 분석 서버로 요청하는 방식으로 수행한다.

##### API Name

```text
POST /api/recall/analyze
```

##### Request Parameter

| 이름 | 타입 | 설명 |
|---|---|---|
| user_id | BIGINT | 사용자 ID |
| recall_question_id | BIGINT | 회상 질문 ID |
| current_record_id | BIGINT | 현재 답변 record_id |
| past_record_id | BIGINT | 과거 기준 답변 record_id |
| transcript_text | TEXT | STT 변환 결과 텍스트 |

##### Request Body 예시

```json
{
  "user_id": 15,
  "recall_question_id": 3,
  "current_record_id": 105,
  "past_record_id": 21,
  "transcript_text": "제 고향은 대구입니다."
}
```

##### Response Body 예시

```json
{
  "similarity_score": 84.0,
  "keyword_score": 91.0,
  "final_recall_score": 88.2,
  "recall_status": "normal"
}
```

---

#### 3.2.10 회상 분석 함수 명세

##### compareRecallAnswer()

| 항목 | 내용 |
|---|---|
| Name | compareRecallAnswer |
| Input | past_text, current_text |
| Process | 과거 답변과 현재 답변을 비교하여 의미 유사도를 계산한다. |
| Output | similarity_score |

---

##### calculateKeywordScore()

| 항목 | 내용 |
|---|---|
| Name | calculateKeywordScore |
| Input | current_text, keyword_list |
| Process | 현재 답변에 핵심 키워드가 포함되어 있는지 확인하여 키워드 일치도 점수를 계산한다. |
| Output | keyword_score |

---

##### calculateRecallScore()

| 항목 | 내용 |
|---|---|
| Name | calculateRecallScore |
| Input | similarity_score, keyword_score, question_type |
| Process | 질문 유형별 가중치를 적용하여 최종 회상 일치도 점수를 계산한다. |
| Output | final_recall_score |

---

##### saveRecallResult()

| 항목 | 내용 |
|---|---|
| Name | saveRecallResult |
| Input | recall_question_id, past_record_id, current_record_id, similarity_score, keyword_score, final_recall_score |
| Process | 회상 분석 결과를 recall_analysis_results 테이블에 저장한다. |
| Output | 저장 성공 여부 |

---

#### 3.2.11 회상 분석 테스트 방안

##### 기능 테스트

| 테스트 항목 | 설명 | 기대 결과 |
|---|---|---|
| 회상 질문 조회 테스트 | recall_question_id로 질문 정보를 조회한다. | 질문 정보가 정상적으로 반환된다. |
| 과거 답변 조회 테스트 | parent_record_id로 기준 답변을 조회한다. | 과거 답변 텍스트가 정상적으로 반환된다. |
| STT 결과 저장 테스트 | 변환된 transcript_text를 저장한다. | audio_records에 텍스트가 저장된다. |
| 키워드 일치도 테스트 | 현재 답변과 핵심 키워드를 비교한다. | keyword_score가 계산된다. |
| 의미 유사도 테스트 | 과거 답변과 현재 답변의 의미 유사도를 계산한다. | similarity_score가 계산된다. |
| 가중치 적용 테스트 | 질문 유형별 가중치를 적용한다. | 유형별 계산 결과가 다르게 산출된다. |
| 최종 점수 저장 테스트 | 회상 분석 결과를 저장한다. | recall_analysis_results에 결과가 저장된다. |

---

### 3.3 종합 위험도 계산 모듈

#### 3.3.1 모듈 개요

종합 위험도 계산 모듈은 음성 특징 분석 결과, 텍스트 분석 결과, 회상 일치도 분석 결과를 종합하여 사용자의 최종 인지기능 저하 위험도를 계산하는 모듈이다.

초기 버전에서는 규칙 기반 가중치 계산 방식을 사용한다. 이후 인지장애 음성 데이터, 구음장애 음성 데이터, 실제 앱 사용 데이터를 활용하여 정상군, 경도인지장애(MCI), 알츠하이머성 치매(AD) 분류 모델로 확장할 수 있도록 설계한다.

본 모듈에서 계산된 결과는 `risk_analysis_results` 테이블에 저장되며, 앱 화면과 보호자 알림 기능에서 활용된다.

---

#### 3.3.2 모듈 기능

| 구분 | 내용 |
|---|---|
| 모듈명 | Risk Scoring Module |
| 주요 기능 | 음성 점수, 텍스트 점수, 회상 점수를 종합하여 최종 위험도 산출 |
| 입력 데이터 | speech_score, text_score, recall_score |
| 처리 방식 | 가중치 기반 점수 계산 |
| 출력 데이터 | final_risk_score, risk_level |
| 저장 테이블 | risk_analysis_results |

---

#### 3.3.3 내부 동작 흐름

```text
[1] speech_analysis_results에서 speech_score 수신
        ↓
[2] text_analysis_results에서 text_score 수신
        ↓
[3] recall_analysis_results에서 recall_score 수신
        ↓
[4] 각 점수에 가중치 적용
        ↓
[5] final_risk_score 계산
        ↓
[6] risk_level 분류
        ↓
[7] risk_analysis_results 테이블에 저장
        ↓
[8] 앱 또는 보호자 화면에 결과 전달
```

---

#### 3.3.4 Input / Process / Output

##### Input

| 입력값 | 설명 |
|---|---|
| session_id | 분석 대상 대화 세션 ID |
| speech_score | 음성 특징 기반 점수 |
| text_score | 텍스트 언어 특징 기반 점수 |
| recall_score | 회상 일치도 기반 점수 |

##### Process

1. 대화 세션 단위로 음성 분석 결과를 조회한다.
2. 대화 세션 단위로 텍스트 분석 결과를 조회한다.
3. 회상 분석 결과에서 회상 일치도 점수를 조회한다.
4. 각 점수에 사전에 정의된 가중치를 적용한다.
5. 최종 위험도 점수인 `final_risk_score`를 계산한다.
6. 계산된 점수를 기준으로 `risk_level`을 분류한다.
7. 결과를 `risk_analysis_results` 테이블에 저장한다.

##### Output

| 출력값 | 설명 |
|---|---|
| final_risk_score | 최종 위험도 점수 |
| risk_level | 위험도 단계 |
| analyzed_at | 위험도 분석 완료 시각 |

---

#### 3.3.5 위험도 계산 방식

초기 버전에서는 다음과 같은 규칙 기반 가중치 계산 방식을 사용한다.

```text
final_risk_score =
(0.4 × recall_score) +
(0.35 × speech_score) +
(0.25 × text_score)
```

회상 일치도는 사용자의 기억 유지 여부를 직접적으로 반영하므로 가장 높은 가중치를 부여한다. 음성 특징은 발화 속도, 침묵 시간, 발음 안정성 등의 변화를 반영하며, 텍스트 특징은 어휘 다양성, 반복 단어 비율, 문장 일관성 등을 반영한다.

---

#### 3.3.6 위험도 단계 분류

| final_risk_score | risk_level | 설명 |
|---|---|---|
| 0 이상 40 미만 | low | 인지기능 저하 위험이 낮은 상태 |
| 40 이상 70 미만 | medium | 지속적인 관찰이 필요한 상태 |
| 70 이상 100 이하 | high | 보호자 확인 및 추가 점검이 필요한 상태 |

---

#### 3.3.7 관련 데이터베이스 구조

##### risk_analysis_results

최종 AI 위험도 분석 결과를 저장하는 테이블이다.

| 속성 | 설명 |
|---|---|
| risk_result_id | 위험도 분석 결과 ID |
| session_id | 분석 대상 세션 ID |
| speech_score | 음성 특징 기반 점수 |
| text_score | 텍스트 특징 기반 점수 |
| recall_score | 회상 일치도 기반 점수 |
| final_risk_score | 최종 위험도 점수 |
| risk_level | 위험도 단계 |
| analyzed_at | 위험도 분석 완료 시각 |

---

#### 3.3.8 위험도 분석 API 인터페이스

##### API Name

```text
GET /api/risk/result/{session_id}
```

##### Response Body 예시

```json
{
  "session_id": 10,
  "speech_score": 72.0,
  "text_score": 81.0,
  "recall_score": 65.0,
  "final_risk_score": 71.0,
  "risk_level": "high",
  "analyzed_at": "2026-05-18T15:30:00"
}
```

---

#### 3.3.9 위험도 계산 함수 명세

##### calculateRiskScore()

| 항목 | 내용 |
|---|---|
| Name | calculateRiskScore |
| Input | speech_score, text_score, recall_score |
| Process | 각 분석 점수에 가중치를 적용하여 final_risk_score를 계산한다. |
| Output | final_risk_score |

---

##### classifyRiskLevel()

| 항목 | 내용 |
|---|---|
| Name | classifyRiskLevel |
| Input | final_risk_score |
| Process | 최종 위험도 점수를 기준으로 low, medium, high 단계를 분류한다. |
| Output | risk_level |

---

##### saveRiskResult()

| 항목 | 내용 |
|---|---|
| Name | saveRiskResult |
| Input | session_id, speech_score, text_score, recall_score, final_risk_score, risk_level |
| Process | 종합 위험도 분석 결과를 risk_analysis_results 테이블에 저장한다. |
| Output | 저장 성공 여부 |

---

#### 3.3.10 위험도 계산 테스트 방안

| 테스트 항목 | 설명 | 기대 결과 |
|---|---|---|
| 점수 입력 테스트 | speech_score, text_score, recall_score를 입력한다. | 입력값이 정상적으로 전달된다. |
| 가중치 계산 테스트 | 각 점수에 가중치를 적용한다. | final_risk_score가 계산된다. |
| 위험 단계 분류 테스트 | final_risk_score를 기준으로 risk_level을 분류한다. | low, medium, high 중 하나로 분류된다. |
| 결과 저장 테스트 | 계산 결과를 DB에 저장한다. | risk_analysis_results에 저장된다. |
| 결과 조회 테스트 | session_id로 위험도 결과를 조회한다. | 앱 또는 보호자 화면에서 결과를 확인할 수 있다. |

---

### 3.4 AI 분석 모듈 확장 방향

초기 구현에서는 회상 일치도, 음성 특징, 텍스트 특징을 규칙 기반 가중치 방식으로 종합하여 위험도를 계산한다.

향후에는 인지장애 음성 데이터셋과 구음장애 음성 데이터셋을 활용하여 정상군, 경도인지장애(MCI), 알츠하이머성 치매(AD)를 분류하는 AI 모델로 확장할 수 있다.

또한 앱 사용자의 장기 사용 데이터를 누적하여 시간 경과에 따른 회상 점수 변화, 음성 특징 변화, 텍스트 특징 변화를 추적하고 개인별 위험도 기준을 보정하는 방식으로 개선할 수 있다.
