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
7. AI 모듈이 최종 위험도 관련 점수를 계산하거나 반환합니다.
8. 백엔드는 분석 결과를 데이터베이스에 저장합니다.

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
음성 파일에서 추출한 음성 특징값을 저장하는 테이블입니다.

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

### final_risk_score
- 타입: FLOAT
- Null 허용: Yes
- 설명: 최종 위험도 점수입니다.

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
