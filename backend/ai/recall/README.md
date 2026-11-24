# 회상 일치도 AI 모듈

이 폴더는 치매 예방/인지기능 점검 앱의 AI 파트 중 **회상 일치도 분석**과 **종합 위험도 계산** 프로토타입을 정리한 코드이다.

## 1. 구성

`dialogue_response_experiment.py`는 이름과 달리 `llm_reply` 엔진의 실행 필수
모듈이다. `voice_reply_handler.py`가 직접 불러오므로 테스트·촬영 자료를
제외할 때 이 파일을 제외하면 서버가 시작되지 않는다.

```text
backend/ai/
 ├─ prototype/
 │   ├─ recall_score_prototype.py
 │   └─ recall_app_prototype.py
 ├─ model/
 │   ├─ train_recall_classifier.py
 │   └─ inference_recall_classifier.py
 ├─ data/
 │   └─ sample_recall_data.csv
 ├─ requirements.txt
 └─ README.md
```

## 2. 주요 기능

### 2.1 회상 일치도 분석

- 과거 기준 답변과 현재 회상 답변 비교
- Sentence-BERT 기반 의미 유사도 계산
- 핵심 키워드 일치도 계산
- 질문 유형별 가중치 적용
- `final_recall_score` 산출

### 2.2 종합 위험도 계산

- `speech_risk_score`: 높을수록 음성 기반 위험이 큼
- `recall_score`: 높을수록 회상 상태가 양호함

최종 합성 비율은 음성 50%, 회상/텍스트 50%다.
회상 전용 계산 함수는 `100 - recall_score`를 사용한다.
실제 세션 집계 경로는 고정 인지 질문과 자연 회상을 합친 `textScore`를 사용한다.

```text
textScore = fixedScore * (3/7) + recallScore * (4/7)
finalRiskScore = speechRiskScore * 0.5 + (100 - textScore) * 0.5
```

두 텍스트 구성 점수가 모두 있을 때의 공식이다. 하나만 있으면 해당 점수를
사용하고 PARTIAL로 표시한다. 음성 근거가 없으면 종합 점수는 보류한다.
이 가중치는 팀 설계값이며 임상 정확도나 검증된 최적 비율을 의미하지 않는다.

개인 정보 질문의 기준 답변이 `HOMETOWN` 같은 항목명뿐이면 채점하지 않는다.
이 경우 `no_fixed_evaluation`과 `personal_reference_not_registered`를 남기며,
회상 점수만 있는 세션을 완전 평가로 승격하지 않는다.
구조화된 단일 회상 대상(음식·장소·사람·물건·매체)은 정확한 대상 답변과
원래 발언의 행동이 일치하면 문장 길이 차이로 감점하지 않는다.
부정·추측·다른 대상 또는 행동이 포함된 경우에는 이 만점 규칙을 적용하지 않는다.
이 규칙은 `verified_target_answer`로 추적하며 기존 저장 점수는 재계산하지 않는다.

서비스 운영용 위험 구간은 다음과 같다.

- `LOW`: 0점 이상 40점 미만
- `MEDIUM`: 40점 이상 60점 미만
- `HIGH`: 60점 이상
- 보호자 알림: `final_risk_score` 60점 이상

위 구간은 임상 진단 기준이 아니라 서비스의 관찰 및 알림을 위한 운영 기준이다.

### 2.3 딥러닝 분류 모델

- 모델: `klue/roberta-base`
- 라벨: `일치`, `부분일치`, `불일치`, `무응답`
- 입력 형태: 질문, 기준답변, 이전답변, 현재답변을 하나의 문장으로 결합

## 3. 실행 방법

### 3.1 패키지 설치

```bash
pip install -r requirements.txt
```

### 3.2 회상 일치도 규칙 기반 프로토타입 실행

```bash
python prototype/recall_score_prototype.py
```

### 3.3 Gradio 프로토타입 실행

```bash
python prototype/recall_app_prototype.py
```

### 3.4 분류 모델 학습

```bash
python model/train_recall_classifier.py --csv_path data/sample_recall_data.csv --output_dir model/midas_recall_model
```

### 3.5 분류 모델 추론

```bash
python model/inference_recall_classifier.py \
  --model_dir model/midas_recall_model \
  --question "고향이 어디인가요?" \
  --expected_answer "대구" \
  --past_answer "제 고향은 대구입니다." \
  --current_answer "저는 대구에서 태어났습니다."
```

## 4. 보안 주의사항

외부 AI Gateway, OpenAI API, AWS S3 등과 연동할 때 API 키를 코드에 직접 작성하지 않는다.

환경변수를 사용한다.

```bash
set YNU_API_KEY=발급받은키
```

또는 `.env` 파일을 사용할 수 있으나 `.env` 파일은 GitHub에 업로드하지 않는다.

## 5. GitHub 업로드 제외 권장 항목

다음 파일은 GitHub에 직접 올리지 않는 것을 권장한다.

```text
.env
__pycache__/
.ipynb_checkpoints/
model/midas_recall_model/
*.pt
*.bin
*.safetensors
```

학습된 모델 파일은 용량이 커질 수 있으므로 필요 시 별도 클라우드 저장소에 보관하고 README에 경로만 작성한다.

## 6. GPT 기반 대화형 회상 질문 생성

`conversation_recall_generator.py`는 사용자의 자유 대화 내용을 기반으로 기억 포인트를 추출하고, 이후 다시 물어볼 자연스러운 회상 질문을 생성한다.

### 6.1 기능

- 자유 대화 transcript 기반 기억 포인트 추출
- 정답을 직접 포함하지 않는 자연스러운 회상 질문 생성
- YNU API Gateway 기반 GPT 호출
- 생성된 질문은 이후 사용자의 답변과 비교하여 회상 일치도 계산에 활용 가능

### 6.2 실행 전 환경변수 설정

```powershell
$env:YNU_API_KEY="발급받은키"
```

### 6.3 실행 방법

```powershell
python conversation_recall_generator.py
```

### 6.4 처리 흐름

```text
자유 대화 transcript
→ GPT 기억 포인트 추출
→ 자연스러운 회상 질문 생성
→ 이후 사용자 답변 수집
→ 기존 대화 내용과 답변 비교
→ 회상 일치도 계산
```

### 6.5 주의사항

- YNU API 키는 GitHub에 업로드하지 않는다.
- 앱에는 API 키를 포함하지 않는다.
- GPT Gateway 호출은 서버 또는 Python 모듈에서만 수행한다.

### 음성 분석 재사용과 지연 관측

같은 녹음의 S3 서명·만료 값만 바뀌면 `TurnAnalysisCache`의 분석 결과를 재사용한다.
호스트·객체 경로·버전·발언·녹음 시각이 바뀌면 다시 분석한다. 실제 다운로드에는
최신 서명 URL을 그대로 사용한다. 현재 업로드는 UUID 객체 키를 사용하므로,
같은 키의 파일을 덮어쓰는 기능을 도입할 때에는 버전 식별자도 전달해야 한다.
`audio_analysis_cache` 로그로 재사용 여부를, `voice_stage`의 `audio_download`,
`whisper`, `stt_refinement`로 음성 처리 지연을 구분한다. 이 로그에는 URL이나 발언을 넣지 않는다.

## 7. memoryPoint 후보 엔진 계약

음성 대화의 중복 판정은 정보 유형뿐 아니라 방금 답변에서 새로 나온 대상도 본다.
`followup_expansion_terms`에 있는 대상을 명시한 활동·매체·물건의 열린 후속 질문은
기존 사건 턴 제한 안에서 허용한다. 부정·거절·정정으로 취소한 내용은 대상에 넣지 않는다.
주제를 바꿨더라도 최근 대화의 같은 사건에서 답한 장소·사람·시간은 다시 묻지 않는다.
명시적으로 다른 날짜나 시간대를 묻는 질문은 같은 사건으로 단정하지 않는다.

회상 질문 생성의 중복 이력은 현재 세션에 연결된 질문·원본 발언만 사용한다.
다른 날의 질문 문장이 비슷하다는 이유로 오늘의 새 기억을 제외하지 않는다.
자유대화의 `session_answered_events`는 최근 12턴 밖의 식사·읽기 정보도 유지한다.
식사 시간대가 불분명할 때는 새 식사로 가정해 재질문하지 않고 필요한 경우에만 확인한다.
명시적인 재택·외출 부정은 시간 범위와 함께 유지하고 이후 외출 발언으로 갱신한다.

`memory_candidate_service.py`는 LLM 호출 전에 대화 원문을 사건과 기억
단서로 구조화한다. 원문을 합치거나 새 사실을 만들지 않는다.

- `eventId`: 같은 사건을 구분하는 안정적인 ID
- `sourceRecordIds`: 사건에 포함된 원본 레코드 ID
- `evidence`: 원문, 턴 순서, 단서와 정정 여부
- `answerValues`: 사건에서 확인된 유형별 단서 전체
- `recallClue`: 이번 회상 질문에 사용할 단서 하나와 원문 출처

```json
{
  "eventId": "ACTIVITY:80",
  "sourceRecordIds": [80, 81],
  "answerValues": {
    "PERSON": ["딸"],
    "PLACE": ["공원"],
    "ACTIVITY": ["산책"]
  },
  "recallClue": {
    "sourceRecordId": 80,
    "sourceText": "딸과 공원에서 산책했어",
    "answerType": "ACTIVITY",
    "answerValue": "산책"
  }
}
```

회상 후보는 독립 사건 2개부터 사용할 수 있다. 대화가 자연스럽게
이어지는 동안에는 3개까지 축적하고, 주제 전환 지점에서 하나를 선택한다.
