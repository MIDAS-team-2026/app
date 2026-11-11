# 회상 일치도 AI 모듈

이 폴더는 치매 예방/인지기능 점검 앱의 AI 파트 중 **회상 일치도 분석**과 **종합 위험도 계산** 프로토타입을 정리한 코드이다.

## 1. 구성

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

- `speech_score`
- `text_score`
- `recall_score`

위 세 점수를 종합하여 `final_risk_score`와 `risk_level`을 계산한다.

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

## 7. memoryPoint 후보 엔진 계약

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
