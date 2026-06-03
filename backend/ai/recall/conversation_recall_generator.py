import os
from typing import Dict, List

from openai import OpenAI


YNU_API_KEY = os.getenv("YNU_API_KEY")
YNU_BASE_URL = "https://factchat-cloud.mindlogic.ai/v1/gateway"
GPT_MODEL = "claude-sonnet-4-6"


client = OpenAI(
    api_key=YNU_API_KEY,
    base_url=YNU_BASE_URL,
)


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
        return {
            "memoryPoint": "",
            "question": "아까 이야기하신 내용 중 기억나는 것이 있으신가요?",
        }

    prompt = f"""
당신은 노인 인지 기능 점검 앱의 자연대화 회상 질문 생성 AI입니다.

목표:
- 아래 대화 내용에서 나중에 다시 물어볼 만한 기억 포인트를 1개 고릅니다.
- 그 기억 포인트를 바탕으로 자연스러운 회상 질문을 1개 만듭니다.
- 질문은 실제 대화처럼 부드럽고 짧게 작성합니다.
- 시험처럼 직접 확인하는 말투는 피합니다.
- 정답을 질문에 직접 포함하지 않습니다.
- 치매 진단처럼 보이는 표현은 사용하지 않습니다.

대화 내용:
{conversation_text}

좋은 질문 예시:
- 아까 말씀하셨던 식사와 관련해서 기억나는 것이 있으신가요?
- 조금 전에 통화하셨다고 하셨는데, 누구와 이야기하셨는지 기억나시나요?
- 오늘 다녀오신 곳에 대해 조금 더 기억나는 게 있으신가요?

나쁜 질문 예시:
- 아까 김치찌개 먹었다고 했죠?
- 아들이랑 통화한 거 맞나요?
- 마트에 다녀왔다고 말했는데 기억하세요?

출력 형식:
memoryPoint: 대화에서 뽑은 기억 포인트
question: 자연스러운 회상 질문
""".strip()

    response = client.chat.completions.create(
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


if __name__ == "__main__":
    sample_conversation = [
        "오늘 아들이랑 통화했어요.",
        "점심에는 김치찌개를 먹었어요.",
        "오후에는 집 근처 마트에 다녀왔어요.",
    ]

    result = generate_recall_question_from_conversation(sample_conversation)

    print("memoryPoint:", result["memoryPoint"])
    print("question:", result["question"])