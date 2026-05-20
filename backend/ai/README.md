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
