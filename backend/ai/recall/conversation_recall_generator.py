import os
import re
from typing import Dict, List, Optional

import requests
from openai import OpenAI


YNU_BASE_URL = "https://factchat-cloud.mindlogic.ai/v1/gateway"
GPT_MODEL = "claude-sonnet-4-6"
BASE_URL = "http://localhost:8080"

_client: OpenAI | None = None


FORBIDDEN_RECALL_KEYWORDS = [
    "성함",
    "이름",
    "생년월일",
    "생년 월일",
    "배우자",
    "고향",
    "요일",
    "날짜",
    "몇 년도",
    "몇년도",
    "몇 월",
    "몇월",
    "며칠",
    "오늘 날짜",
    "오늘은 무슨 요일",
]


WEAK_MEMORY_PHRASES = [
    "응",
    "네",
    "아니",
    "몰라",
    "모르겠",
    "기억 안",
    "기억이 안",
    "그냥",
    "없어",
    "없다",
    "없었",
    "없어요",
    "없다니까",
]


MEMORY_DETAIL_HINTS = [
    "먹",
    "마시",
    "갔",
    "다녀",
    "왔",
    "만났",
    "봤",
    "보았",
    "했",
    "샀",
    "사왔",
    "넣었",
    "전화",
    "통화",
    "이야기",
    "산책",
    "운동",
    "병원",
    "아팠",
    "마트",
    "시장",
    "공원",
    "집",
    "티비",
    "텔레비전",
    "뉴스",
    "노래",
    "드라마",
    "사과",
    "우유",
    "생선",
    "냉장고",
    "약",
    "허리",
    "몸",
    "불편",
    "손주",
    "아들",
    "딸",
    "동생",
    "형",
    "누나",
    "언니",
    "오빠",
    "엄마",
    "아빠",
    "어머니",
    "아버지",
    "남편",
    "아내",
    "조카",
    "사촌",
    "친구",
    "가족",
    "점심",
    "저녁",
    "아침",
    "음식",
]

MEMORY_ACTION_HINTS = [
    "먹었",
    "마셨",
    "갔",
    "갔다",
    "다녀왔",
    "봤",
    "보고",
    "보았",
    "들었",
    "샀",
    "넣었",
    "만났",
    "통화",
    "전화",
    "이야기",
    "있었",
    "앉았",
    "누워",
]


MEMORY_CONCRETE_HINTS = [
    "김치",
    "볶음밥",
    "피자",
    "라면",
    "국",
    "밥",
    "사과",
    "우유",
    "생선",
    "약",
    "허리",
    "몸",
    "티비",
    "텔레비전",
    "뉴스",
    "노래",
    "드라마",
    "병원",
    "마트",
    "시장",
    "공원",
    "집",
    "냉장고",
    "소파",
    "아들",
    "딸",
    "손주",
    "동생",
    "형",
    "누나",
    "언니",
    "오빠",
    "엄마",
    "아빠",
    "어머니",
    "아버지",
    "남편",
    "아내",
    "조카",
    "사촌",
    "친구",
]


FUTURE_OR_WISH_PHRASES = [
    "먹고 싶",
    "먹고싶",
    "보고 싶",
    "보고싶",
    "하고 싶",
    "하고싶",
]

INCOMPLETE_OR_NEGATED_ACTION_PHRASES = [
    "안 먹",
    "못 먹",
    "아직 안",
    "아직은 안",
    "연락은 못",
    "연락을 못",
    "안 나갔",
    "못 나갔",
]


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


def is_forbidden_recall_content(text: str) -> bool:
    text = clean_text(text)

    if not text:
        return True

    return any(keyword in text for keyword in FORBIDDEN_RECALL_KEYWORDS)


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


def evaluate_memory_candidate(text: str) -> Dict[str, object]:
    text = clean_text(text)

    reasons = []
    score = 0

    if not is_valid_conversation_text(text):
        return {
            "isValid": False,
            "score": 0,
            "reasons": ["too_short_or_meaningless"],
        }

    if is_forbidden_recall_content(text):
        return {
            "isValid": False,
            "score": 0,
            "reasons": ["forbidden_fixed_question_content"],
        }

    if any(phrase in text for phrase in WEAK_MEMORY_PHRASES):
        reasons.append("weak_or_uncertain_expression")
        score -= 20

    if len(text) >= 8:
        score += 20
        reasons.append("enough_length")

    if len(text) >= 14:
        score += 15
        reasons.append("specific_length")

    detail_hits = [
        hint
        for hint in MEMORY_DETAIL_HINTS
        if hint in text
    ]

    if detail_hits:
        score += min(45, len(detail_hits) * 15)
        reasons.append("has_daily_detail")

    if re.search(r"\d", text):
        score += 10
        reasons.append("has_number_detail")

    score = max(0, min(score, 100))

    return {
        "isValid": score >= 30,
        "score": score,
        "reasons": reasons,
    }


def score_recall_memory_candidate(text: str) -> Dict[str, object]:
    text = clean_text(text)
    evaluation = evaluate_memory_candidate(text)

    if not evaluation["isValid"]:
        return {
            **evaluation,
            "recallScore": 0,
        }

    recall_score = int(evaluation["score"])
    reasons = list(evaluation["reasons"])

    action_hits = [hint for hint in MEMORY_ACTION_HINTS if hint in text]
    concrete_hits = [hint for hint in MEMORY_CONCRETE_HINTS if hint in text]

    if action_hits:
        recall_score += min(30, len(action_hits) * 10)
        reasons.append("has_past_action")

    if concrete_hits:
        recall_score += min(30, len(concrete_hits) * 10)
        reasons.append("has_concrete_object")

    if any(phrase in text for phrase in FUTURE_OR_WISH_PHRASES):
        recall_score -= 25
        reasons.append("future_or_wish_expression")

    if any(phrase in text for phrase in INCOMPLETE_OR_NEGATED_ACTION_PHRASES):
        recall_score -= 50
        reasons.append("incomplete_or_negated_action")

    if any(phrase in text for phrase in WEAK_MEMORY_PHRASES):
        recall_score -= 35
        reasons.append("low_info_expression")

    recall_score = max(0, min(recall_score, 100))

    return {
        **evaluation,
        "isValid": recall_score >= 40,
        "recallScore": recall_score,
        "reasons": reasons,
    }


def select_recall_memory_candidates(
    conversation_history: List[str],
    max_candidates: int = 3,
) -> List[str]:
    scored_candidates = []

    for index, text in enumerate(conversation_history):
        cleaned = clean_text(text)
        evaluation = score_recall_memory_candidate(cleaned)

        if evaluation["isValid"]:
            scored_candidates.append(
                {
                    "index": index,
                    "text": cleaned,
                    "score": evaluation["recallScore"],
                    "reasons": evaluation["reasons"],
                }
            )

    scored_candidates = [
        item
        for item in scored_candidates
        if item["score"] >= 50 or "has_concrete_object" in item["reasons"]
    ]

    scored_candidates.sort(key=lambda item: item["score"], reverse=True)
    selected = sorted(scored_candidates[:max_candidates], key=lambda item: item["index"])

    return [item["text"] for item in selected]


def filter_valid_conversation_history(
    conversation_history: List[str],
) -> List[str]:
    valid_history = []

    for text in conversation_history:
        cleaned = clean_text(text)

        evaluation = evaluate_memory_candidate(cleaned)

        if evaluation["isValid"]:
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
        category = str(item.get("category", "")).strip()

        if category == "INITIAL_FIXED":
            continue

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
    valid_conversation_history = select_recall_memory_candidates(
        conversation_history
    )

    conversation_text = build_conversation_text(valid_conversation_history)

    if not conversation_text:
        return {
            "status": "SKIPPED",
            "reason": "회상 질문을 만들 만큼 의미 있는 자유대화 내용이 부족합니다.",
            "memoryPoint": "",
            "question": "",
        }

    previous_questions_text = build_previous_questions_text(previous_questions)
    used_memory_points_text = build_used_memory_points_text(used_memory_points)

    prompt = f"""
당신은 노인과 자연스럽게 대화를 이어가는 한국어 AI 말동무입니다.

이 앱의 목적:
- 사용자가 검사받는 느낌을 받지 않도록 일상 대화처럼 이어갑니다.
- 사용자의 자유 대화 내용에서 나중에 다시 물어볼 회상 질문을 만듭니다.
- 회상 질문은 인지 기능 점검에 활용되지만, 사용자는 일반 대화처럼 느껴야 합니다.

해야 할 일:
1. 아래 최근 자유 대화 내용에서 나중에 다시 물어볼 만한 기억 포인트를 1개 고릅니다.
2. 이미 물어본 질문과 겹치지 않는 새로운 회상 질문을 1개 만듭니다.
3. 이미 사용한 기억 포인트와 같은 내용은 피합니다.
4. 질문은 자연스럽고 짧게 작성합니다.
5. 전체 질문은 1문장 또는 짧은 2문장으로 작성합니다.
6. 정답을 질문에 직접 포함하지 않습니다.
7. 치매, 검사, 기억력 테스트, 진단, 정답, 오답 같은 표현은 사용하지 않습니다.
8. 성함, 배우자, 고향, 요일, 날짜, 생년월일, 년도, 월, 일 관련 질문은 절대 만들지 않습니다.
9. 회상 질문은 반드시 사용자가 자유롭게 말한 일상 내용에서만 만듭니다.

최근 자유 대화 내용:
{conversation_text}

이미 물어본 회상 질문:
{previous_questions_text}

이미 사용한 기억 포인트:
{used_memory_points_text}

절대 생성하면 안 되는 질문:
- 성함이 어떻게 되시나요?
- 생년월일이 어떻게 되시나요?
- 배우자분 성함이 어떻게 되시나요?
- 고향이 어디신가요?
- 오늘은 무슨 요일인가요?
- 오늘 날짜가 어떻게 되나요?
- 지금 몇 년도인가요?
- 지금 몇 월인가요?
- 오늘이 며칠인가요?

좋은 질문 예시:
- 그러고 보니 아까 음식 이야기를 해주셨잖아요. 어떤 음식을 드셨는지 기억나세요?
- 아까 다녀오신 곳 이야기를 해주셨는데, 어디에 다녀오셨는지 기억나세요?
- 조금 전에 통화 이야기를 해주셨는데, 누구와 통화하셨는지 기억나세요?

나쁜 질문 예시:
- 아까 김치찌개 먹었다고 했죠?
- 아들이랑 통화한 거 맞나요?
- 마트에 다녀왔다고 말했는데 기억하세요?
- 기억력 확인을 위해 질문드릴게요.
- 오늘은 무슨 요일인가요?
- 생년월일이 어떻게 되시나요?

출력 형식:
memoryPoint: 대화에서 뽑은 기억 포인트
question: 자연스러운 회상 질문
""".strip()

    response = _get_client().chat.completions.create(
        model=GPT_MODEL,
        messages=[
            {
                "role": "system",
                "content": "당신은 한국어 자연 회상 질문을 생성하는 AI입니다. 초기 고정 질문이나 지남력 질문은 절대 만들지 않습니다.",
            },
            {
                "role": "user",
                "content": prompt,
            },
        ],
        temperature=0.45,
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

    memory_evaluation = score_recall_memory_candidate(memory_point)

    if not memory_evaluation["isValid"]:
        return {
            "status": "SKIPPED",
            "reason": "회상 질문으로 저장하기에는 memoryPoint가 너무 짧거나 구체성이 부족합니다.",
            "memoryPoint": memory_point,
            "question": question,
            "memoryQualityScore": memory_evaluation["recallScore"],
            "memoryQualityReasons": memory_evaluation["reasons"],
        }

    if is_forbidden_recall_content(memory_point) or is_forbidden_recall_content(question):
        return {
            "status": "SKIPPED",
            "reason": "초기 고정 질문 또는 지남력 질문과 유사한 내용이 생성되어 저장하지 않았습니다.",
            "memoryPoint": memory_point,
            "question": question,
        }

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
        "memoryQualityScore": memory_evaluation["recallScore"],
        "memoryQualityReasons": memory_evaluation["reasons"],
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
        "조금 전에 통화 이야기를 해주셨는데, 누구와 통화하셨는지 기억나세요?"
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
