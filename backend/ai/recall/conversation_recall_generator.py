import os
from typing import Dict, List

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
        _client = OpenAI(api_key=api_key, base_url=YNU_BASE_URL)
    return _client


def build_conversation_text(conversation_history: List[str]) -> str:
    lines = []

    for index, text in enumerate(conversation_history, start=1):
        text = str(text).strip()

        if text:
            lines.append(f"{index}. {text}")

    return "\n".join(lines)


def generate_recall_question_from_conversation(
    conversation_history: List[str],
) -> Dict[str, str]:
    conversation_text = build_conversation_text(conversation_history)

    if not conversation_text:
        # 대화 내용이 없으면 Claude에게 자연스러운 대화 시작 인사 생성을 맡김
        prompt = """
당신은 노인과 따뜻하게 대화를 나누는 친근한 AI 말동무입니다.

지금 막 대화를 시작하려고 합니다. 대화 내용은 아직 없습니다.
자연스럽고 따뜻한 대화 시작 인사를 한 문장으로 만들어주세요.

조건:
- 어르신을 편안하게 해주는 말투
- 오늘 하루나 최근 일상에 관심을 보이는 내용
- 너무 딱딱하거나 질문지처럼 보이지 않게

출력 형식:
memoryPoint:
question: 자연스러운 대화 시작 인사
""".strip()
    else:
        prompt = f"""
당신은 노인과 따뜻하게 대화를 나누는 친근한 AI 말동무입니다.

목표:
- 아래 대화 내용에서 나중에 다시 물어볼 만한 기억 포인트를 1개 고릅니다.
- 대화 내용에 공감하는 짧은 미사여구로 시작한 뒤, 자연스러운 회상 질문을 이어서 작성합니다.
- 전체 답변은 1~2문장으로 짧고 부드럽게 작성합니다.
- 시험처럼 직접 확인하는 말투는 피합니다.
- 정답을 질문에 직접 포함하지 않습니다.
- 치매 진단처럼 보이는 표현은 사용하지 않습니다.

대화 내용:
{conversation_text}

좋은 답변 예시:
- 아이고, 맛있는 걸 드셨군요~ 그때 드셨던 음식 중에 기억나는 게 있으세요?
- 그렇군요, 바쁜 하루를 보내셨네요! 오늘 다녀오신 곳이 기억나시나요?
- 오, 반가운 분이랑 통화하셨군요~ 누구와 이야기 나누셨는지 기억나세요?

나쁜 답변 예시:
- 아까 김치찌개 먹었다고 했죠?
- 아들이랑 통화한 거 맞나요?
- 마트에 다녀왔다고 말했는데 기억하세요?

출력 형식:
memoryPoint: 대화에서 뽑은 기억 포인트
question: 공감 미사여구 + 자연스러운 회상 질문
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
        temperature=0.7,
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

    return {
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


if __name__ == "__main__":
    sample_conversation = [
        "오늘 아들이랑 통화했어요.",
        "점심에는 김치찌개를 먹었어요.",
        "오후에는 집 근처 마트에 다녀왔어요.",
    ]

    result = generate_recall_question_from_conversation(sample_conversation)

    print("memoryPoint:", result["memoryPoint"])
    print("question:", result["question"])

    saved_question = save_recall_question_to_spring(
        user_id=1,
        memory_point=result["memoryPoint"],
        question_text=result["question"],
    )

    print("savedQuestion:", saved_question)