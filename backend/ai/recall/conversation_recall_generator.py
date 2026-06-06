import os
from typing import Dict, List
import requests
from openai import OpenAI
import random

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


def build_conversation_text_with_ids(records: List[dict]) -> tuple[str, List[int]]:
    lines = []
    record_ids = []
    for index, r in enumerate(records, start=1):
        text = str(r.get("transcriptText", "")).strip()
        r_id = r.get("recordId")
        if r_id:
            record_ids.append(int(r_id))
        if text:
            lines.append(f"{index}. {text}")
    return "\n".join(lines), record_ids


def generate_recall_question_from_conversation(
        user_id: int,
        session_id: int,
        records: List[dict],
) -> Dict[str, any]:
    from .conversation_recall_generator import build_conversation_text_with_ids
    conversation_text, all_record_ids = build_conversation_text_with_ids(records)

    try:
        from .recall_api_client import get_recall_questions
        past_questions_dict = get_recall_questions(user_id, BASE_URL)
        past_questions = sorted(list(past_questions_dict.values()), key=lambda x: int(x.get('questionId', 0)))
    except Exception as e:
        print("과거 질문 조회 실패:", e)
        past_questions = []

    # 실제 정답으로 처리된 ID만 수집
    asked_ids = {int(r.get("recallQuestionId")) for r in records if str(r.get("answerRole")) == "RECALL" and r.get("recallQuestionId") is not None}
    all_ai_replies = "".join([str(r.get("replyText", "")) for r in records]).replace(" ", "")

    available_questions = []
    for q in past_questions:
        q_id = int(q.get("questionId"))
        q_text = str(q.get("questionText", "")).strip()
        keyword_check = q_text[:10].replace(" ", "")
        if q_id in asked_ids or keyword_check in all_ai_replies:
            continue
        available_questions.append(q)

    if not available_questions and past_questions:
        available_questions = past_questions

    # 🔥 [핵심 로직] 타이밍 분리
    turn_count = len(records)
    is_asking_turn = (turn_count % 2 == 0) # AI가 과거 질문을 던져야 하는 턴 (짝수)
    is_answering_turn = (turn_count % 2 == 1 and turn_count > 1) # 노인이 방금 AI 질문에 대답한 턴 (홀수)

    target_id = None
    link_id_for_this_record = None
    past_memory_text = "사용 가능한 기억이 없습니다."

    if is_asking_turn:
        if available_questions:
            random.seed(session_id + turn_count)
            selected_target = random.choice(available_questions)
            target_id = int(selected_target.get('questionId'))
            past_memory_text = f"- ID: {target_id}, 내용: {selected_target.get('questionText')}, 정답: {selected_target.get('expectedAnswer')}"

        strategy_guide = f"""
        [명령] 이번 턴은 노인에게 과거 기억을 확인하는 **[RECALL]** 전략입니다.
        반드시 아래 지정된 단 하나의 과거 정보만 사용하여, 확인 질문을 던지세요.
        지정된 과거 정보: {past_memory_text}
        (예시: "어르신, 지난번에 ~라고 하셨는데 맞나요?")
        """
        allowed_format = "RECALL"

    elif is_answering_turn:
        if available_questions:
            random.seed(session_id + turn_count - 1)
            selected_target = random.choice(available_questions)
            link_id_for_this_record = int(selected_target.get('questionId'))

        strategy_guide = """
        [명령] 이번 턴은 노인이 직전 질문에 대답한 턴입니다. 자연스럽게 공감하며 가벼운 꼬리 질문(FOLLOW_UP)을 하세요.
        절대 또 다른 과거 기억(RECALL)을 연속해서 묻지 마세요.
        대신 노인의 이번 대답에서 나중에 물어볼 만한 '새로운 사실(음식, 일상 등)'이 있다면 'newMemoryPoint'와 'newQuestionText'를 추출하세요. 없다면 None.
        """
        allowed_format = "FOLLOW_UP"

    else:
        strategy_guide = """
        [명령] 첫 대화입니다. 반갑게 인사하고 일상적인 가벼운 질문(FOLLOW_UP)을 하세요.
        노인의 첫 대답에서 나중에 물어볼 만한 새로운 사실이 있다면 'newMemoryPoint'와 'newQuestionText'를 추출하세요. 없다면 None.
        """
        allowed_format = "FOLLOW_UP"

    prompt = f"""
당신은 노인과 대화하는 친절한 AI입니다.

[대화 내역]
{conversation_text}

{strategy_guide.strip()}

[출력 형식 - 반드시 엄수]
strategy: {allowed_format}
newMemoryPoint: (추출할 기억 사실 정보, 없을 시 None)
newQuestionText: (그 기억을 검증할 질문 문장, 없을 시 None)
question: AI가 보낼 최종 대화 및 질문
""".strip()

    response = _get_client().chat.completions.create(
        model=GPT_MODEL,
        messages=[
            # 🔥 [가장 중요한 시스템 룰] 길이 제한 및 무조건 질문 생성 강제
            {
                "role": "system",
                "content": """당신은 노인 친화형 대화 AI입니다. 아래 3가지 절대 규칙을 무조건 지키세요.
1. [길이 제한] 답변은 무조건 1~2문장으로 아주 짧고 간결하게 작성하세요. 수다를 떨지 마세요.
2. [질문 필수] 혼자 말하고 끝내지 마세요. 모든 답변의 마지막은 반드시 노인에게 묻는 '질문(물음표 ?)'으로 끝나야 합니다.
3. [태도] 노인이 편안하게 대답할 수 있도록 다정하게 물어보세요."""
            },
            {"role": "user", "content": prompt},
        ],
        temperature=0.1, # 창의성을 낮추고 규칙 준수율을 극대화
    )

    content = response.choices[0].message.content.strip()

    strategy = ""
    new_memory_point = None
    new_question_text = None
    question_lines = []
    is_question_section = False

    for line in content.splitlines():
        stripped_line = line.strip()
        if stripped_line.startswith("strategy:"):
            strategy = stripped_line.replace("strategy:", "").strip()
            is_question_section = False
        elif stripped_line.startswith("newMemoryPoint:"):
            val = stripped_line.replace("newMemoryPoint:", "").replace("None", "").strip()
            if val: new_memory_point = val
            is_question_section = False
        elif stripped_line.startswith("newQuestionText:"):
            val = stripped_line.replace("newQuestionText:", "").replace("None", "").strip()
            if val: new_question_text = val
            is_question_section = False
        elif stripped_line.startswith("question:"):
            question_lines.append(stripped_line.replace("question:", "").strip())
            is_question_section = True
        elif is_question_section:
            question_lines.append(stripped_line)

    question = "\n".join(question_lines).strip()
    if not question: question = content

    if is_asking_turn:
        strategy = "RECALL"
    else:
        strategy = "FOLLOW_UP"

    return {
        "strategy": strategy,
        "recallQuestionId": link_id_for_this_record,
        "newMemoryPoint": new_memory_point,
        "newQuestionText": new_question_text,
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