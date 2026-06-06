import os
import re
from typing import Dict, List, Optional

import requests
from openai import OpenAI


YNU_BASE_URL = "https://factchat-cloud.mindlogic.ai/v1/gateway"
GPT_MODEL = "claude-sonnet-4-6"
BASE_URL = "http://localhost:8080"

_client: OpenAI | None = None


def _get_client() -> OpenAI:
    global _client

    if _client is None:
        api_key = os.getenv("YNU_API_KEY")

        if not api_key:
            raise EnvironmentError("환경변수 YNU_API_KEY가 설정되지 않았습니다.")

        _client = OpenAI(
            api_key=api_key,
            base_url=YNU_BASE_URL,
        )

    return _client


def clean_text(text: str) -> str:
    text = str(text).strip()
    text = re.sub(r"\s+", " ", text)
    return text


def is_valid_conversation_text(text: str) -> bool:
    text = clean_text(text)

    if not text:
        return False

    if len(text) < 5:
        return False

    meaningless_words = {
        "음",
        "어",
        "아",
        "네",
        "응",
        "예",
        "몰라",
        "모르겠어",
        "글쎄",
    }

    if text in meaningless_words:
        return False

    if len(set(text)) <= 2:
        return False

    return True


def filter_valid_conversation_history(
    conversation_history: List[str],
) -> List[str]:
    valid_history = []

    for text in conversation_history:
        cleaned = clean_text(text)

        if is_valid_conversation_text(cleaned):
            valid_history.append(cleaned)

    return valid_history


def build_conversation_text(conversation_history: List[str]) -> str:
    lines = []

    for index, text in enumerate(conversation_history, start=1):
        text = clean_text(text)

        if text:
            lines.append(f"{index}. {text}")

    return "\n".join(lines)


def build_previous_questions_text(previous_questions: Optional[List[str]]) -> str:
    if not previous_questions:
        return "없음"

    lines = []

    for index, question in enumerate(previous_questions, start=1):
        question = clean_text(question)

        if question:
            lines.append(f"{index}. {question}")

    return "\n".join(lines) if lines else "없음"


def build_used_memory_points_text(
    used_memory_points: Optional[List[str]],
) -> str:
    if not used_memory_points:
        return "없음"

    lines = []

    for index, memory_point in enumerate(used_memory_points, start=1):
        memory_point = clean_text(memory_point)

        if memory_point:
            lines.append(f"{index}. {memory_point}")

    return "\n".join(lines) if lines else "없음"


def fetch_existing_recall_questions(
    user_id: int,
    base_url: str = BASE_URL,
) -> Dict[str, List[str]]:
    response = requests.get(
        f"{base_url}/api/recall/questions/{user_id}",
        timeout=10,
    )

    response.raise_for_status()

    questions = response.json()

    previous_questions = []
    used_memory_points = []

    for item in questions:
        question_text = str(item.get("questionText", "")).strip()
        expected_answer = str(item.get("expectedAnswer", "")).strip()

        if question_text:
            previous_questions.append(question_text)

        if expected_answer:
            used_memory_points.append(expected_answer)

    return {
        "previousQuestions": previous_questions,
        "usedMemoryPoints": used_memory_points,
    }


def generate_recall_question_from_conversation(
    conversation_history: List[str],
    previous_questions: Optional[List[str]] = None,
    used_memory_points: Optional[List[str]] = None,
) -> Dict[str, str]:
    valid_conversation_history = filter_valid_conversation_history(
        conversation_history
    )

    conversation_text = build_conversation_text(valid_conversation_history)

    if not conversation_text:
        return {
            "status": "SKIPPED",
            "reason": "회상 질문을 만들 만큼 의미 있는 대화 내용이 부족합니다.",
            "memoryPoint": "",
            "question": "",
        }

    previous_questions_text = build_previous_questions_text(previous_questions)
    used_memory_points_text = build_used_memory_points_text(used_memory_points)

    prompt = f"""
당신은 노인과 따뜻하게 대화를 나누는 한국어 AI 말동무입니다.

이 앱의 목적:
- 사용자가 검사받는 느낌을 받지 않도록 자연스럽게 대화합니다.
- 사용자의 과거 대화 내용을 바탕으로 나중에 다시 물어볼 회상 질문을 만듭니다.
- 회상 질문은 인지 기능 점검에 활용되지만, 사용자는 일상 대화처럼 느껴야 합니다.

해야 할 일:
1. 아래 최근 대화 내용에서 나중에 다시 물어볼 만한 기억 포인트를 1개 고릅니다.
2. 이미 물어본 질문과 겹치지 않는 새로운 회상 질문을 1개 만듭니다.
3. 이미 사용한 기억 포인트와 같은 내용은 피합니다.
4. 질문은 공감 표현을 포함해 자연스럽고 부드럽게 작성합니다.
5. 전체 질문은 1문장 또는 짧은 2문장으로 작성합니다.
6. 정답을 질문에 직접 포함하지 않습니다.
7. 치매, 검사, 기억력 테스트, 진단 같은 표현은 사용하지 않습니다.

최근 대화 내용:
{conversation_text}

이미 물어본 회상 질문:
{previous_questions_text}

이미 사용한 기억 포인트:
{used_memory_points_text}

좋은 질문 예시:
- 아이고, 맛있는 걸 드셨군요. 그때 드셨던 음식 중에 기억나는 게 있으세요?
- 그렇군요, 바쁜 하루를 보내셨네요. 오늘 다녀오신 곳이 기억나시나요?
- 오, 반가운 분과 이야기하셨군요. 누구와 통화하셨는지 기억나세요?

나쁜 질문 예시:
- 아까 김치찌개 먹었다고 했죠?
- 아들이랑 통화한 거 맞나요?
- 마트에 다녀왔다고 말했는데 기억하세요?
- 기억력 확인을 위해 질문드릴게요.

출력 형식:
memoryPoint: 대화에서 뽑은 기억 포인트
question: 공감 표현이 포함된 자연스러운 회상 질문
""".strip()

    response = _get_client().chat.completions.create(
        model=GPT_MODEL,
        messages=[
            {
                "role": "system",
                "content": "당신은 한국어 자연 회상 질문을 생성하는 AI입니다.",
            },
            {
                "role": "user",
                "content": prompt,
            },
        ],
        temperature=0.6,
    )

    content = response.choices[0].message.content.strip()

    memory_point = ""
    question = ""

    for line in content.splitlines():
        line = line.strip()

        if line.startswith("memoryPoint:"):
            memory_point = line.replace("memoryPoint:", "").strip()
        elif line.startswith("question:"):
            question = line.replace("question:", "").strip()

    if not question:
        question = content

    if previous_questions:
        normalized_question = clean_text(question)

        for previous_question in previous_questions:
            if clean_text(previous_question) == normalized_question:
                return {
                    "status": "SKIPPED",
                    "reason": "이미 생성된 질문과 동일한 질문이 생성되어 저장하지 않았습니다.",
                    "memoryPoint": memory_point,
                    "question": question,
                }

    return {
        "status": "CREATED",
        "reason": "",
        "memoryPoint": memory_point,
        "question": question,
    }


def save_recall_question_to_spring(
    user_id: int,
    memory_point: str,
    question_text: str,
    base_url: str = BASE_URL,
) -> Dict:
    body = {
        "userId": user_id,
        "questionText": question_text,
        "questionType": "RECALL",
        "category": "CONVERSATION",
        "expectedAnswer": memory_point,
    }

    response = requests.post(
        f"{base_url}/api/recall/questions",
        json=body,
        timeout=10,
    )

    response.raise_for_status()
    return response.json()


def generate_and_save_recall_question(
    user_id: int,
    conversation_history: List[str],
    previous_questions: Optional[List[str]] = None,
    used_memory_points: Optional[List[str]] = None,
    base_url: str = BASE_URL,
) -> Dict:
    if previous_questions is None or used_memory_points is None:
        existing = fetch_existing_recall_questions(
            user_id=user_id,
            base_url=base_url,
        )

        if previous_questions is None:
            previous_questions = existing["previousQuestions"]

        if used_memory_points is None:
            used_memory_points = existing["usedMemoryPoints"]

    result = generate_recall_question_from_conversation(
        conversation_history=conversation_history,
        previous_questions=previous_questions,
        used_memory_points=used_memory_points,
    )

    if result.get("status") != "CREATED":
        return result

    saved_question = save_recall_question_to_spring(
        user_id=user_id,
        memory_point=result["memoryPoint"],
        question_text=result["question"],
        base_url=base_url,
    )

    return {
        **result,
        "savedQuestion": saved_question,
    }


if __name__ == "__main__":
    sample_conversation = [
        "오늘 아들이랑 통화했어요.",
        "점심에는 김치찌개를 먹었어요.",
        "오후에는 집 근처 마트에 다녀왔어요.",
    ]

    sample_previous_questions = [
        "오, 반가운 분과 이야기하셨군요. 누구와 통화하셨는지 기억나세요?"
    ]

    sample_used_memory_points = [
        "아들과 통화했다는 것"
    ]

    result = generate_recall_question_from_conversation(
        conversation_history=sample_conversation,
        previous_questions=sample_previous_questions,
        used_memory_points=sample_used_memory_points,
    )

    print("status:", result["status"])
    print("memoryPoint:", result["memoryPoint"])
    print("question:", result["question"])