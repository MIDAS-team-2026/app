"""
회상 일치도 분류 모델 추론 코드

학습된 모델 디렉터리를 이용하여 현재 답변이
일치 / 부분일치 / 불일치 / 무응답 중 어디에 가까운지 예측한다.

실행 예시:
    python model/inference_recall_classifier.py \
        --model_dir model/midas_recall_model \
        --question "고향이 어디인가요?" \
        --expected_answer "대구" \
        --past_answer "제 고향은 대구입니다." \
        --current_answer "저는 대구에서 태어났습니다."
"""

from __future__ import annotations

import argparse

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer


def make_input_text(
    question: str,
    expected_answer: str,
    past_answer: str,
    current_answer: str,
) -> str:
    return (
        f"질문: {question} "
        f"[SEP] 기준답변: {expected_answer} "
        f"[SEP] 이전답변: {past_answer} "
        f"[SEP] 현재답변: {current_answer}"
    )


def predict(
    model_dir: str,
    question: str,
    expected_answer: str,
    past_answer: str,
    current_answer: str,
) -> dict:
    tokenizer = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModelForSequenceClassification.from_pretrained(model_dir)

    text = make_input_text(
        question=question,
        expected_answer=expected_answer,
        past_answer=past_answer,
        current_answer=current_answer,
    )

    inputs = tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        padding="max_length",
        max_length=160,
    )

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=-1)[0]
        pred_id = int(torch.argmax(probs).item())

    id2label = model.config.id2label
    label = id2label.get(pred_id, str(pred_id))

    return {
        "label": label,
        "confidence": round(float(probs[pred_id]), 4),
        "probabilities": {
            id2label.get(i, str(i)): round(float(prob), 4)
            for i, prob in enumerate(probs)
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_dir", default="model/midas_recall_model")
    parser.add_argument("--question", required=True)
    parser.add_argument("--expected_answer", required=True)
    parser.add_argument("--past_answer", required=True)
    parser.add_argument("--current_answer", required=True)
    args = parser.parse_args()

    result = predict(
        model_dir=args.model_dir,
        question=args.question,
        expected_answer=args.expected_answer,
        past_answer=args.past_answer,
        current_answer=args.current_answer,
    )
    print(result)


if __name__ == "__main__":
    main()
