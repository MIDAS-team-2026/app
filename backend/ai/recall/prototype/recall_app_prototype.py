"""
회상 일치도 앱 프로토타입

Colab에서 작성한 Gradio 기반 프로토타입을 정리한 파일이다.
외부 API 키는 코드에 직접 작성하지 않고 환경변수로 주입한다.

환경변수:
    YNU_API_KEY

실행:
    python prototype/recall_app_prototype.py
"""

from __future__ import annotations

import os
import random
from datetime import datetime
from zoneinfo import ZoneInfo

import gradio as gr


DB = {
    "users": [],
    "chat_sessions": [],
    "audio_records": [],
    "recall_questions": [],
    "risk_analysis_results": [],
    "recall_analysis_results": [],
}

AUTO_ID = {
    "user_id": 1,
    "session_id": 1,
    "record_id": 1,
    "question_id": 1,
    "risk_result_id": 1,
    "recall_result_id": 1,
}


def next_id(name: str) -> int:
    value = AUTO_ID[name]
    AUTO_ID[name] += 1
    return value


def now_korea() -> datetime:
    return datetime.now(ZoneInfo("Asia/Seoul"))


def normalize_text(text: str) -> str:
    if text is None:
        return ""

    text = str(text).strip()
    remove_words = [" ", ".", ",", "입니다", "이에요", "예요", "요", "네", "음", "어"]
    for word in remove_words:
        text = text.replace(word, "")

    return text


def is_no_answer(text: str) -> bool:
    text = normalize_text(text)

    no_answer_words = [
        "모르겠",
        "기억안나",
        "기억이나지않",
        "생각안나",
        "잘몰라",
        "글쎄",
        "헷갈려",
        "몰라",
    ]

    return any(word in text for word in no_answer_words)


def simple_similarity(a: str, b: str) -> float:
    a = normalize_text(a)
    b = normalize_text(b)

    if not a or not b:
        return 0.0

    if a in b or b in a:
        return 1.0

    set_a = set(a)
    set_b = set(b)

    common = set_a & set_b
    union = set_a | set_b

    return len(common) / max(len(union), 1)


def compare_answer(expected: str, current: str, question_type: str) -> tuple[str, int, float]:
    if current is None or str(current).strip() == "":
        return "무응답", 20, 0.0

    if is_no_answer(current):
        return "무응답", 20, 0.0

    similarity = simple_similarity(expected, current)

    if similarity >= 0.75:
        return "일치", 0, similarity
    if similarity >= 0.45:
        return "부분일치", 10, similarity

    if question_type == "ORIENTATION":
        return "불일치", 20, similarity

    return "불일치", 25, similarity


def get_risk_level(score: int) -> str:
    if score >= 70:
        return "HIGH"
    if score >= 40:
        return "MEDIUM"
    return "LOW"


def make_summary(
    profile_score: int,
    orientation_score: int,
    recall_score: int,
    no_answer_count: int,
    level: str,
) -> str:
    reasons = []

    if profile_score >= 10:
        reasons.append("개인정보 기반 질문에서 일부 불일치가 감지되었습니다")

    if orientation_score >= 10:
        reasons.append("날짜와 요일 등 지남력 질문에서 혼동이 감지되었습니다")

    if recall_score >= 10:
        reasons.append("기억회상 질문에서 이전 답변과 현재 답변의 불일치 또는 무응답이 감지되었습니다")

    if no_answer_count >= 2:
        reasons.append("무응답 또는 기억 회피 표현이 반복적으로 나타났습니다")

    if not reasons:
        return "현재 대화에서는 뚜렷한 인지 위험 신호가 감지되지 않았습니다."

    return f"{', '.join(reasons)}. 전체 위험도 단계는 {level}입니다."


def run_simple_demo(name: str, hometown_answer: str, breakfast_answer: str) -> str:
    user_id = next_id("user_id")
    session_id = next_id("session_id")

    DB["users"].append({
        "user_id": user_id,
        "name": name,
        "created_at": now_korea().isoformat(),
    })
    DB["chat_sessions"].append({
        "session_id": session_id,
        "user_id": user_id,
        "started_at": now_korea().isoformat(),
    })

    expected_hometown = "대구"
    expected_breakfast = "미역국"

    hometown_result, hometown_penalty, hometown_similarity = compare_answer(
        expected_hometown,
        hometown_answer,
        "PROFILE",
    )
    breakfast_result, breakfast_penalty, breakfast_similarity = compare_answer(
        expected_breakfast,
        breakfast_answer,
        "RECALL",
    )

    final_risk_score = hometown_penalty + breakfast_penalty
    risk_level = get_risk_level(final_risk_score)

    summary = make_summary(
        profile_score=hometown_penalty,
        orientation_score=0,
        recall_score=breakfast_penalty,
        no_answer_count=sum([
            1 if hometown_result == "무응답" else 0,
            1 if breakfast_result == "무응답" else 0,
        ]),
        level=risk_level,
    )

    return (
        f"사용자: {name}\n"
        f"고향 답변 판정: {hometown_result} / 유사도: {hometown_similarity:.2f}\n"
        f"아침 식사 답변 판정: {breakfast_result} / 유사도: {breakfast_similarity:.2f}\n"
        f"최종 위험도 점수: {final_risk_score}\n"
        f"위험도 단계: {risk_level}\n"
        f"요약: {summary}"
    )


def build_app() -> gr.Interface:
    return gr.Interface(
        fn=run_simple_demo,
        inputs=[
            gr.Textbox(label="사용자 이름", value="테스트사용자"),
            gr.Textbox(label="고향 질문 답변", value="대구에서 태어났어요"),
            gr.Textbox(label="아침 식사 질문 답변", value="미역국 먹었어요"),
        ],
        outputs=gr.Textbox(label="분석 결과", lines=10),
        title="회상 일치도 분석 프로토타입",
        description="과거 기준 답변과 현재 답변의 일치 정도를 간단히 평가하는 Gradio 프로토타입입니다.",
    )


if __name__ == "__main__":
    # 외부 AI Gateway를 사용할 경우 아래 환경변수를 사용한다.
    # 코드에는 API 키를 직접 작성하지 않는다.
    yuh_api_key = os.getenv("YNU_API_KEY")
    app = build_app()
    app.launch()
