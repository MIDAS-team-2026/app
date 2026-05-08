# AI DB 요구사항 정리

---

## 1. 목적

앱 데이터베이스에는 실제 사용자가 앱에서 녹음한 기록과 AI 분석 결과를 저장합니다.

---

## 2. 전체 저장 흐름

1. 사용자가 앱에서 자유대화를 녹음합니다.
2. 음성 파일을 서버 또는 저장소에 저장합니다.
3. 데이터베이스에는 음성 파일 자체가 아니라 파일 경로와 기본 정보를 저장합니다.
4. AI 모듈이 음성 파일에서 음성 특징을 추출합니다.
5. STT 기능이 있다면 음성을 텍스트로 변환합니다.
6. 변환된 텍스트를 기반으로 텍스트 특징을 분석합니다.
7. 회상 질문의 경우, 저장된 기준 답변과 현재 STT 답변을 비교합니다.
8. AI 모듈은 의미 유사도와 핵심 키워드 일치도를 기반으로 회상 일치도 점수를 계산합니다.
9. 음성 분석 결과, 텍스트 분석 결과, 회상 일치도 점수를 종합하여 최종 위험도 점수를 계산합니다.
10. 백엔드는 분석 결과를 데이터베이스에 저장합니다.

---

## 3. 필요 테이블

### users
사용자 정보를 저장하는 테이블입니다.

### audio_records
사용자의 녹음 기록과 STT 결과를 저장하는 테이블입니다.

### speech_analysis_results
음성 파일에서 추출한 음성 특징 분석 결과를 저장하는 테이블입니다.

### text_analysis_results
STT 전사 텍스트에서 추출한 텍스트 분석 결과를 저장하는 테이블입니다.

### recall_questions
AI가 사용자에게 제시하는 회상 질문 정보를 저장하는 테이블입니다.

### recall_answers
사용자의 초기 답변과 회상 답변 정보를 저장하는 테이블입니다.

### recall_keywords
회상 질문 및 답변의 핵심 키워드를 저장하는 테이블입니다.

### recall_analysis_results
과거 답변과 현재 답변을 비교한 회상 일치도 분석 결과를 저장하는 테이블입니다.

### risk_analysis_results
최종 AI 위험도 분석 결과를 저장하는 테이블입니다.

---

## 4. audio_records 테이블
사용자의 녹음 1건에 대한 기본 정보를 저장하는 테이블입니다.

### record_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 녹음 기록 ID입니다.

### user_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 사용자 ID입니다.

### audio_file_path
- 타입: VARCHAR
- Null 허용: No
- 설명: 저장된 음성 파일의 경로입니다.
- 예시: /audio/user3/record_001.wav

### audio_duration
- 타입: FLOAT
- Null 허용: No
- 설명: 전체 녹음 길이입니다.
- 단위: seconds
- 예시: 35.42

### transcript_text
- 타입: TEXT
- Null 허용: Yes
- 설명: STT를 통해 음성을 텍스트로 변환한 결과입니다.
- 비고: STT가 아직 구현되지 않은 경우 null로 둘 수 있습니다.

### stt_confidence
- 타입: FLOAT
- Null 허용: Yes
- 설명: STT 변환 신뢰도입니다.
- 비고: 사용하는 STT API에서 신뢰도 값을 제공하지 않으면 null로 둘 수 있습니다.

### recorded_at
- 타입: DATETIME
- Null 허용: No
- 설명: 녹음이 생성된 시각입니다.

## 5. speech_analysis_results 테이블
음성 파일에서 추출한 음성 특징값을 및 구음장애 관련 특징값을 저장하는 테이블입니다.

### speech_analysis_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 음성 분석 결과 ID입니다.

### record_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 녹음 기록 ID입니다.
- 비고: audio_records 테이블의 record_id와 연결됩니다.

### pause_count
- 타입: INT
- Null 허용: No
- 설명: 침묵 구간의 개수입니다.

### total_pause_duration
- 타입: FLOAT
- Null 허용: No
- 설명: 전체 침묵 시간입니다.
- 단위: seconds

### avg_pause_duration
- 타입: FLOAT
- Null 허용: No
- 설명: 평균 침묵 시간입니다.
- 단위: seconds

### max_pause_duration
- 타입: FLOAT
- Null 허용: No
- 설명: 가장 긴 침묵 시간입니다.
- 단위: seconds

### rms_mean
- 타입: FLOAT
- Null 허용: No
- 설명: 평균 음성 에너지(소리의 크기와 세기)입니다.

### rms_std
- 타입: FLOAT
- Null 허용: No
- 설명: 음성 에너지의 표준편차입니다.

### zcr_mean
- 타입: FLOAT
- Null 허용: No
- 설명: Zero Crossing Rate의 평균값입니다.

### zcr_std
- 타입: FLOAT
- Null 허용: No
- 설명: Zero Crossing Rate의 표준편차입니다.

### spectral_centroid_mean
- 타입: FLOAT
- Null 허용: No
- 설명: 주파수 중심값의 평균입니다.

### spectral_centroid_std
- 타입: FLOAT
- Null 허용: No
- 설명: 주파수 중심값의 표준편차입니다.

### mfcc_1_mean
- 타입: FLOAT
- Null 허용: No
- 설명: MFCC 1번 계수의 평균값입니다.

### mfcc_2_mean
- 타입: FLOAT
- Null 허용: No
- 설명: MFCC 2번 계수의 평균값입니다.

### mfcc_3_mean
- 타입: FLOAT
- Null 허용: No
- 설명: MFCC 3번 계수의 평균값입니다.

### mfcc_4_mean
- 타입: FLOAT
- Null 허용: No
- 설명: MFCC 4번 계수의 평균값입니다.

### mfcc_5_mean
- 타입: FLOAT
- Null 허용: No
- 설명: MFCC 5번 계수의 평균값입니다.

### speech_rate
- 타입: FLOAT
- Null 허용: Yes
- 설명: 말속도입니다.

### articulation_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 발음 명확도 및 조음 정확도 기반 점수입니다.
- 비고: 구음장애 특징 분석에 활용할 수 있습니다.

### pronunciation_stability
- 타입: FLOAT
- Null 허용: Yes
- 설명: 발음 안정성 관련 특징값입니다.
- 비고: 발음 흔들림 및 발화 불안정성 분석에 활용할 수 있습니다.

### response_latency
- 타입: FLOAT
- Null 허용: Yes
- 설명: 질문이 끝난 뒤 사용자가 첫 발화를 시작하기까지 걸린 시간입니다.
- 단위: seconds

### repetition_count
- 타입: INT
- Null 허용: Yes
- 설명: 반복된 단어 또는 표현의 개수입니다.

### filler_count
- 타입: INT
- Null 허용: Yes
- 설명: “음”, “어”, “그”, “저기” 같은 filler 표현의 개수입니다.

### analyzed_at
- 타입: DATETIME
- Null 허용: No
- 설명: 음성 분석이 완료된 시각입니다.

## 6. text_analysis_results 테이블
STT 전사 텍스트에서 추출한 언어적 특징을 저장하는 테이블입니다.

### text_analysis_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 텍스트 분석 결과 ID입니다.

### record_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 녹음 기록 ID입니다.
- 연결: audio_records 테이블의 record_id와 연결됩니다.

### word_count
- 타입: INT
- Null 허용: Yes
- 설명: 전체 단어 수입니다.

### sentence_count
- 타입: INT
- Null 허용: Yes
- 설명: 전체 문장 수입니다.

### avg_sentence_length
- 타입: FLOAT
- Null 허용: Yes
- 설명: 평균 문장 길이입니다.

### lexical_diversity
- 타입: FLOAT
- Null 허용: Yes
- 설명: 어휘 다양성 점수입니다.

### repeated_word_ratio
- 타입: FLOAT
- Null 허용: Yes
- 설명: 반복 단어 비율입니다.

### incomplete_sentence_count
- 타입: INT
- Null 허용: Yes
- 설명: 불완전 문장 수입니다.

### topic_coherence_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 문장 간 의미 연결성 점수입니다.

### analyzed_at
- 타입: DATETIME
- Null 허용: No
- 설명: 텍스트 분석이 완료된 시각입니다.

## 7. risk_analysis_results 테이블
AI 분석 결과를 종합한 최종 위험도 결과를 저장하는 테이블입니다.

### risk_result_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 위험도 분석 결과 ID입니다.

### record_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 녹음 기록 ID입니다.
- 연결: audio_records 테이블의 record_id와 연결됩니다.

### speech_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 음성 특징 기반 점수입니다.

### text_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 텍스트 특징 기반 점수입니다.

### recall_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 회상 일치도 기반 점수입니다.

### final_risk_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 음성 점수, 텍스트 점수, 회상 일치도 점수를 종합한 최종 위험도 점수입니다.

### risk_level
- 타입: VARCHAR
- Null 허용: Yes
- 설명: 위험도 단계입니다.
- 예시: low, medium, high

### analyzed_at
- 타입: DATETIME
- Null 허용: No
- 설명: 위험도 분석이 완료된 시각입니다.

## 8. 테이블 관계
### users와 audio_records
- users 1 : N audio_records
### audio_records와 speech_analysis_results
- 1 : 1
### audio_records와 text_analysis_results
- 1 : 1 
### audio_records와 risk_analysis_results
- 1 : 1
### users와 recall_questions
- users 1 : N recall_questions
### recall_questions와 recall_answers
- 1 : N
### recall_questions와 recall_keywords
- 1 : N
### audio_records와 recall_answers
- 1 : 1
- 비고: 회상 답변 음성 파일과 연결됩니다.
### recall_answers와 recall_analysis_results
- 1 : 1
### recall_analysis_results와 risk_analysis_results
- 비고: recall_analysis_results의 문항별 회상 점수는 사용자별 또는 녹음 세션별로 요약되어 risk_analysis_results의 recall_score 계산에 활용됩니다.

## 9. 회상 일치도 평가 기능

### 개요
회상 일치도 평가는 사용자의 일반 발화에서 과거 기억을 자동으로 추출하는 방식이 아니라, 초기 질문 및 설문 응답을 통해 기준 기억 데이터를 저장한 뒤 이후 동일하거나 유사한 질문을 다시 제시하여 과거 답변과 현재 답변의 일치도를 비교하는 방식으로 수행합니다.

초기 질문은 고향, 배우자 이름, 가족 관계, 첫 회사, 좋아하는 음식, 자녀 이름, 손자/손녀 정보 등 사용자의 장기 기억 및 개인 생활 정보 중심으로 구성합니다.

또한 오늘 날짜, 오늘 먹은 아침밥, 현재 계절과 같은 단기 기억 및 시간 지남력 관련 질문을 매일 또는 주기적으로 제시할 수 있습니다.

앱은 TTS 기능을 통해 AI 질문을 음성으로 출력하고, 사용자의 음성 답변은 STT를 통해 텍스트로 변환합니다. 변환된 텍스트는 DB에 저장된 과거 답변과 비교되며, Python AI 서버는 의미 유사도, 핵심 키워드 일치도, 질문 유형별 가중치를 기반으로 최종 회상 일치도 점수를 계산합니다.

## 10. recall_questions 테이블
회상 질문 정보를 저장하는 테이블입니다.

### recall_question_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 질문 ID입니다.

### user_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 사용자 ID입니다.

### question_text
- 타입: TEXT
- Null 허용: No
- 설명: AI가 사용자에게 제시한 질문 내용입니다.

### question_type
- 타입: VARCHAR
- Null 허용: No
- 설명: 질문 유형입니다.
- 예시: FACT, PREFERENCE, MEMORY, DAILY

### category
- 타입: VARCHAR
- Null 허용: Yes
- 설명: 질문 카테고리입니다.
- 예시: 가족, 개인정보, 취향, 장기기억, 단기기억

### created_at
- 타입: DATETIME
- Null 허용: No
- 설명: 질문 생성 시각입니다.

## 11. recall_answers 테이블
사용자의 회상 답변 정보를 저장하는 테이블입니다.

### recall_answer_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 답변 ID입니다.

### recall_question_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 질문 ID입니다.
- 연결: recall_questions 테이블의 recall_question_id와 연결됩니다.

### user_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 사용자 ID입니다.

### record_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 답변에 해당하는 녹음 기록 ID입니다.
- 연결: audio_records 테이블의 record_id와 연결됩니다.

### answer_type
- 타입: VARCHAR
- Null 허용: No
- 설명: 답변 유형입니다.
- 예시: INITIAL, RECALL

### created_at
- 타입: DATETIME
- Null 허용: No
- 설명: 답변 생성 시각입니다.

## 12. recall_keywords 테이블
회상 질문의 핵심 키워드를 저장하는 테이블입니다.

### keyword_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 키워드 ID입니다.

### recall_question_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 질문 ID입니다.
- 연결: recall_questions 테이블의 recall_question_id와 연결됩니다.

### keyword_text
- 타입: VARCHAR
- Null 허용: No
- 설명: 핵심 키워드입니다.

## 13. recall_analysis_results 테이블
회상 일치도 분석 결과를 저장하는 테이블입니다.

### recall_result_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 분석 결과 ID입니다.

### recall_question_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 회상 질문 ID입니다.

### past_answer_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 비교 기준이 되는 초기 답변 ID입니다.

### current_answer_id
- 타입: BIGINT 또는 UUID
- Null 허용: No
- 설명: 현재 회상 답변 ID입니다.

### similarity_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 과거 답변과 현재 답변의 의미 유사도 점수입니다.

### keyword_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 핵심 키워드 일치도 점수입니다.

### final_recall_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 최종 회상 일치도 점수입니다.

### analyzed_at
- 타입: DATETIME
- Null 허용: No
- 설명: 회상 분석 완료 시각입니다.

## 14. 회상 점수 계산 방식

회상 일치도 점수는 다음 요소를 기반으로 계산합니다.

- 의미 유사도(SBERT 기반)
- 핵심 키워드 일치도
- 질문 유형별 가중치

질문 유형은 다음과 같이 구분합니다.

### FACT
고향, 배우자 이름, 혈액형 등 정답 키워드 중심 질문입니다.
- 의미 유사도: 20%
- 키워드 일치도: 80%

### PREFERENCE
좋아하는 음식, 계절, 가수 등 선호 기반 질문입니다.
- 의미 유사도: 40%
- 키워드 일치도: 60%

### MEMORY
가장 좋았던 기억, 직업 경험 등 장기 기억 기반 질문입니다.
- 의미 유사도: 60%
- 키워드 일치도: 40%

### DAILY
오늘 날짜, 오늘 식사 등 단기 기억 및 시간 지남력 질문입니다.
- 의미 유사도: 30%
- 키워드 일치도: 70%

최종 점수 기준:
- 80 이상: 정상
- 50 이상 80 미만: 주의
- 50 미만: 위험

## 15. 치매 및 구음장애 데이터 기반 위험도 모델 확장 방향

향후 인지기능 장애 진단 음성/대화 데이터셋과 구음장애 음성 데이터를 활용하여 정상군, MCI(경도인지장애), AD(알츠하이머성 치매) 환자군의 음성 및 언어 특징을 학습할 수 있습니다.

인지기능 장애 데이터셋은 치매 및 인지기능장애 환자의 음성 데이터를 기반으로 치매진단 알고리즘 개발을 목적으로 구축된 데이터입니다.

또한 구음장애 데이터셋을 활용하여 발음 명확도, 발화 안정성, 말속도 저하, 긴 침묵 구간 등 치매 환자에게서 나타날 수 있는 음성 특징을 함께 분석할 수 있습니다.

본 프로젝트에서는 앱 사용자의 음성 분석 결과, 텍스트 분석 결과, 회상 일치도 결과를 종합하여 치매 위험도 판단 기준을 보정하는 데 활용할 수 있습니다.

### 음성 기반 feature
- 침묵 구간 개수
- 전체 침묵 시간
- 평균 침묵 시간
- 최대 침묵 시간
- 음성 에너지 평균
- 음성 에너지 표준편차
- MFCC 계수
- 말속도
- 질문 후 첫 발화까지의 반응 시간
- filler 표현 개수
- 발음 명확도 점수
- 발화 안정성 점수
- 조음 정확도

### 텍스트 기반 feature
- 전체 단어 수
- 문장 수
- 평균 문장 길이
- 어휘 다양성
- 반복 단어 비율
- 불완전 문장 수
- 문장 간 의미 연결성 점수

### 회상 일치도 기반 feature
- 평균 회상 점수
- 최저 회상 점수
- 낮은 점수 개수
- 평균 키워드 일치도
- 평균 의미 유사도
- 질문 유형별 회상 점수 변화
- 시간 경과에 따른 회상 점수 변화

최종 위험도 점수는 음성 기반 점수, 텍스트 기반 점수, 회상 일치도 점수를 종합하여 계산합니다. 초기 버전에서는 규칙 기반 가중치 방식으로 계산하고, 이후 인지장애/구음장애 데이터셋을 활용하여 정상군, MCI, AD 분류 모델을 학습하거나 기존 위험도 기준을 보정하는 방식으로 확장할 수 있습니다.
