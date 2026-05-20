"""
회상 일치도 분류 모델 학습 코드

라벨:
- 일치
- 부분일치
- 불일치
- 무응답

입력 CSV 필수 컬럼:
- question
- expected_answer
- past_answer
- current_answer
- label

실행 예시:
    python model/train_recall_classifier.py \
        --csv_path data/sample_recall_data.csv \
        --output_dir model/midas_recall_model

주의:
    학습된 모델 디렉터리는 용량이 클 수 있으므로 GitHub에는 직접 올리지 않고,
    필요 시 별도 저장소 또는 클라우드 스토리지 링크로 관리한다.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd
from datasets import Dataset
from sklearn.metrics import accuracy_score, f1_score
from sklearn.model_selection import train_test_split
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
)


LABEL2ID = {
    "일치": 0,
    "부분일치": 1,
    "불일치": 2,
    "무응답": 3,
}

ID2LABEL = {value: key for key, value in LABEL2ID.items()}


def make_input_text(row: pd.Series) -> str:
    return (
        f"질문: {row['question']} "
        f"[SEP] 기준답변: {row['expected_answer']} "
        f"[SEP] 이전답변: {row['past_answer']} "
        f"[SEP] 현재답변: {row['current_answer']}"
    )


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    preds = np.argmax(logits, axis=-1)

    return {
        "accuracy": accuracy_score(labels, preds),
        "macro_f1": f1_score(labels, preds, average="macro"),
    }


def train(csv_path: str, output_dir: str, model_name: str = "klue/roberta-base") -> None:
    df = pd.read_csv(csv_path)

    required_columns = {
        "question",
        "expected_answer",
        "past_answer",
        "current_answer",
        "label",
    }
    missing = required_columns - set(df.columns)
    if missing:
        raise ValueError(f"CSV에 필요한 컬럼이 없습니다: {sorted(missing)}")

    df["text"] = df.apply(make_input_text, axis=1)
    df["labels"] = df["label"].map(LABEL2ID)

    if df["labels"].isna().any():
        raise ValueError("label 컬럼에는 일치, 부분일치, 불일치, 무응답 중 하나만 사용할 수 있습니다.")

    train_df, test_df = train_test_split(
        df,
        test_size=0.2,
        random_state=42,
        stratify=df["labels"],
    )

    train_dataset = Dataset.from_pandas(train_df[["text", "labels"]])
    test_dataset = Dataset.from_pandas(test_df[["text", "labels"]])

    tokenizer = AutoTokenizer.from_pretrained(model_name)

    model = AutoModelForSequenceClassification.from_pretrained(
        model_name,
        num_labels=4,
        id2label=ID2LABEL,
        label2id=LABEL2ID,
    )

    def tokenize_function(batch):
        return tokenizer(
            batch["text"],
            truncation=True,
            padding="max_length",
            max_length=160,
        )

    train_tokenized = train_dataset.map(tokenize_function, batched=True)
    test_tokenized = test_dataset.map(tokenize_function, batched=True)

    train_tokenized = train_tokenized.remove_columns(["text"])
    test_tokenized = test_tokenized.remove_columns(["text"])

    train_tokenized.set_format("torch")
    test_tokenized.set_format("torch")

    training_args = TrainingArguments(
        output_dir=output_dir,
        eval_strategy="epoch",
        save_strategy="epoch",
        learning_rate=2e-5,
        per_device_train_batch_size=8,
        per_device_eval_batch_size=8,
        num_train_epochs=8,
        weight_decay=0.01,
        logging_steps=5,
        load_best_model_at_end=True,
        metric_for_best_model="macro_f1",
        greater_is_better=True,
        report_to="none",
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_tokenized,
        eval_dataset=test_tokenized,
        compute_metrics=compute_metrics,
    )

    trainer.train()
    print(trainer.evaluate())

    Path(output_dir).mkdir(parents=True, exist_ok=True)
    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv_path", default="data/sample_recall_data.csv")
    parser.add_argument("--output_dir", default="model/midas_recall_model")
    parser.add_argument("--model_name", default="klue/roberta-base")
    args = parser.parse_args()

    train(
        csv_path=args.csv_path,
        output_dir=args.output_dir,
        model_name=args.model_name,
    )


if __name__ == "__main__":
    main()
