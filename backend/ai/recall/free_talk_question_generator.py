import json
import os
import re
from typing import Any, List

try:
    from openai import OpenAI
except ImportError:
    OpenAI = None


YNU_BASE_URL = "https://factchat-cloud.mindlogic.ai/v1/gateway"
GPT_MODEL = "claude-sonnet-4-6"

_client: Any = None


FORBIDDEN_QUESTION_PATTERNS = [
    "성함",
    "이름",
    "배우자",
    "고향",
    "요일",
    "날짜",
    "생년월일",
    "나이",
    "몇 년",
    "몇 월",
    "며칠",
    "치매",
    "검사",
    "진단",
    "정답",
    "맞히",
    "틀렸",
    "기억력",
]


NEGATIVE_ASSUMPTION_PATTERNS = [
    "걱정",
    "불안",
    "우울",
    "외롭",
    "힘들",
    "무섭",
    "신경 쓰",
]


OVERLY_CLINICAL_EMPATHY_PATTERNS = [
    "상담",
    "치료",
    "진료",
    "우울증",
    "불안장애",
    "극복",
    "힘내",
    "괜찮아질",
]


QUESTION_STOPWORDS = {
    "요즘",
    "오늘",
    "최근에",
    "혹시",
    "조금",
    "다시",
    "있으세요",
    "기억나세요",
    "떠오르세요",
    "어떠셨어요",
    "무엇",
    "뭐가",
    "어떤",
    "하나",
}


EVERYDAY_TOPIC_EXAMPLES = [
    "보고 싶은 사람",
    "오늘 먹은 음식",
    "다녀온 장소",
    "집에서 한 일",
    "본 방송이나 들은 노래",
    "산책이나 이동",
    "어릴 적 기억",
    "최근 떠오른 물건",
    "동네 풍경",
    "계절이나 날씨",
    "시장이나 마트",
    "명절이나 가족 행사",
    "사진이나 오래된 물건",
    "취미나 손으로 하던 일",
]


POSITIVE_CUE_PATTERNS = [
    "좋",
    "재밌",
    "즐거",
    "맛있",
    "상쾌",
    "편안",
    "기쁘",
    "반가",
]


NEGATIVE_CUE_PATTERNS = [
    "아프",
    "아팠",
    "다쳤",
    "넘어",
    "심심",
    "외롭",
    "힘들",
    "불편",
    "속상",
    "무서",
    "걱정",
]


TOPIC_SIMILARITY_GROUPS = [
    ("방송", "프로그램", "텔레비전", "티비", "tv", "TV", "노래", "가수", "트롯", "미스터트롯", "임영웅"),
    ("집", "집안", "집밖", "집 밖", "집에", "집에서", "방", "거실"),
    ("동네", "풍경", "바깥", "밖에", "나가", "다녀온", "초록", "산책", "공원", "길"),
    ("음식", "식사", "밥", "아침", "점심", "저녁", "김치볶음밥", "피자", "약"),
    ("가족", "아들", "딸", "손주", "배우자", "자식", "연락"),
    ("병원", "약", "진료", "의사", "간호사", "아프", "다쳤", "무릎"),
    ("날씨", "바람", "비", "눈", "햇빛", "더워", "추워", "쌀쌀"),
]


def _get_client() -> Any:
    global _client

    if OpenAI is None:
        raise ImportError("openai package is not installed.")

    if _client is None:
        api_key = os.getenv("YNU_API_KEY")

        if not api_key:
            raise EnvironmentError("YNU_API_KEY is not set.")

        _client = OpenAI(
            api_key=api_key,
            base_url=YNU_BASE_URL,
        )

    return _client


def clean_text(text: str) -> str:
    text = str(text or "").strip()
    text = re.sub(r"\s+", " ", text)
    return text


def _normalize_similarity_word(word: str) -> str:
    word = clean_text(word)

    for suffix in (
        "\uc5d0\uc11c\ub294",
        "\uc5d0\uc11c",
        "\uc73c\ub85c",
        "\uc5d0\uac8c",
        "\ud55c\ud14c",
        "\uc774\ub098",
        "\ub791",
        "\uc640",
        "\uacfc",
        "\ub85c",
        "\uc740",
        "\ub294",
        "\uc774",
        "\uac00",
        "\uc744",
        "\ub97c",
        "\uc5d0",
        "\uc758",
        "\ub3c4",
        "\ub9cc",
    ):
        if len(word) > len(suffix) + 1 and word.endswith(suffix):
            return word[: -len(suffix)]

    return word


def normalize_question_for_similarity(text: str) -> str:
    text = clean_text(text).replace("?", "")
    text = re.sub(r"[^\w\s가-힣]", "", text)
    text = re.sub(r"\s+", " ", text)
    words = [_normalize_similarity_word(word) for word in text.strip().split()]
    return " ".join(word for word in words if word)


def _has_empathy_prefix(question: str) -> bool:
    question = clean_text(question)
    prefixes = (
        "\uc88b\uc73c\uc168\uaca0\uc5b4\uc694",
        "\uc7ac\ubbf8\uc788\uc73c\uc168\uaca0\uc5b4\uc694",
        "\uadf8\ub7ec\uc168\uad70\uc694",
        "\uc544\ud558",
        "\uc74c",
        "\uc544\uc774\uace0",
    )
    return question.startswith(prefixes)


def _shares_topic_group(text: str, previous_text: str) -> bool:
    text = clean_text(text).lower()
    previous_text = clean_text(previous_text).lower()

    if not text or not previous_text:
        return False

    for group in TOPIC_SIMILARITY_GROUPS:
        normalized_group = [term.lower() for term in group]
        if any(term in text for term in normalized_group) and any(
            term in previous_text for term in normalized_group
        ):
            return True

    return False


def _format_history(conversation_history: List[str]) -> str:
    lines = []

    for index, text in enumerate(conversation_history, start=1):
        cleaned = clean_text(text)

        if cleaned:
            lines.append(f"{index}. {cleaned}")

    return "\n".join(lines) if lines else "없음"


def _format_topic_examples() -> str:
    return ", ".join(EVERYDAY_TOPIC_EXAMPLES)


def _extract_json_object(text: str) -> dict:
    text = clean_text(text)

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{.*\}", text, re.DOTALL)

    if not match:
        return {}

    try:
        return json.loads(match.group(0))
    except json.JSONDecodeError:
        return {}


def is_safe_followup_question(question: str) -> bool:
    question = clean_text(question)

    if not question:
        return False

    if len(question) < 8 or len(question) > 95:
        return False

    if question.count("?") > 1:
        return False

    if not question.endswith("?"):
        return False

    if any(pattern in question for pattern in FORBIDDEN_QUESTION_PATTERNS):
        return False

    if any(pattern in question for pattern in NEGATIVE_ASSUMPTION_PATTERNS):
        return False

    if any(pattern in question for pattern in OVERLY_CLINICAL_EMPATHY_PATTERNS):
        return False

    return True


def is_weak_free_talk_answer(text: str) -> bool:
    text = clean_text(text)

    if not text:
        return True

    exact_weak_answers = {
        "응",
        "네",
        "아니",
        "몰라",
        "없어",
        "글쎄",
    }

    if text in exact_weak_answers:
        return True

    weak_phrases = [
        "모르겠",
        "없어요",
        "딱히",
        "그냥",
        "기억 안",
    ]

    return any(phrase in text for phrase in weak_phrases)


def build_fallback_with_empathy(
    question: str,
    conversation_history: List[str],
) -> str:
    latest_text = clean_text(conversation_history[-1] if conversation_history else "")
    question = clean_text(question)

    if not latest_text or not question:
        return question

    if _has_empathy_prefix(question):
        return question

    if any(pattern in latest_text for pattern in NEGATIVE_CUE_PATTERNS):
        return f"그러셨군요. {question}"

    if any(pattern in latest_text for pattern in POSITIVE_CUE_PATTERNS):
        return f"좋으셨겠어요. {question}"

    return question


def is_similar_to_previous_question(
    question: str,
    previous_questions: List[str],
) -> bool:
    question = normalize_question_for_similarity(question)
    question_words = {
        word
        for word in question.split()
        if len(word) >= 2 and word not in QUESTION_STOPWORDS
    }

    if not question_words:
        return False

    for previous_question in previous_questions:
        previous = normalize_question_for_similarity(previous_question)
        previous_words = {
            word
            for word in previous.split()
            if len(word) >= 2 and word not in QUESTION_STOPWORDS
        }

        if not previous_words:
            continue

        if _shares_topic_group(question, previous):
            return True

        if question[:12] and question[:12] == previous[:12]:
            return True

        overlap = len(question_words & previous_words)
        smaller_size = min(len(question_words), len(previous_words))

        if overlap >= 2:
            return True

        if smaller_size > 0 and overlap / smaller_size >= 0.45:
            return True

    return False


def generate_safe_followup_question(
    conversation_history: List[str],
    stage: str,
    fallback_question: str,
    previous_questions: List[str] | None = None,
) -> dict:
    stage = str(stage or "").upper()
    fallback_question = build_fallback_with_empathy(
        fallback_question,
        conversation_history,
    )
    previous_questions = previous_questions or []

    if stage not in {"OPEN", "DEEPEN", "ANCHOR"}:
        return {
            "nextQuestion": fallback_question,
            "shouldChangeTopic": False,
            "reason": "unsupported_stage",
        }

    prompt = f"""
당신은 고령자와 자연스럽게 대화하는 한국어 말동무 AI입니다.

대화 목표:
- 검사처럼 느껴지지 않게 편안하게 대화를 이어갑니다.
- 자유대화 3턴 안에서 회상 질문으로 쓸 수 있는 구체적인 단서를 모읍니다.
- 한 주제를 너무 오래 캐묻지 않고, 현재 3턴 흐름 안에서만 자연스럽게 구체화합니다.
- 정해진 질문지를 반복하지 말고, 사용자의 말에서 핵심 단서를 잡아 이어 묻습니다.
- 새 주제를 열 때는 매번 비슷한 사람/음식 질문만 반복하지 말고 다양한 일상 주제를 사용합니다.
- 사용자의 말이 긍정적이면 짧게 좋은 반응을 하고, 부정적이면 짧게 받아준 뒤 자연스럽게 이어 묻습니다.

열 수 있는 일상 주제 예시:
{_format_topic_examples()}

현재 단계:
{stage}

단계 의미:
- OPEN: 새로운 자유대화 주제를 엽니다. 너무 넓지 않게 사람, 음식, 장소, 집에서 한 일, 방송/노래 같은 일상 주제 중 하나를 자연스럽게 묻습니다.
- DEEPEN: 사용자가 방금 말한 내용을 한 번 더 구체화합니다. 이유, 누구와, 어디서, 언제쯤 중 자연스러운 하나만 묻습니다.
- ANCHOR: 나중에 회상 질문으로 만들 수 있게 장면, 장소, 사람, 시간 중 하나를 구체적으로 묻습니다.

최근 자유대화 답변:
{_format_history(conversation_history)}

이미 물어본 질문:
{_format_history(previous_questions)}

반드시 지킬 규칙:
1. 질문은 한국어 1문장만 만듭니다.
2. 질문은 반드시 물음표로 끝납니다.
3. 이름, 배우자, 고향, 날짜, 요일, 나이, 생년월일은 묻지 않습니다.
4. 치매, 검사, 진단, 정답, 오답, 기억력 같은 표현은 쓰지 않습니다.
5. 사용자의 말을 부정적으로 넘겨짚지 않습니다.
6. 너무 넓은 질문은 피합니다.
7. 이미 물어본 질문과 같은 질문은 만들지 않습니다.
8. 이미 물어본 질문과 의도가 비슷한 질문도 만들지 않습니다.
9. 사용자가 답한 내용을 정답처럼 확인하지 말고, 편하게 이어 묻습니다.
10. 사용자의 답변 안에 있는 핵심 단어를 그대로 복사만 하지 말고, 그 단서에서 자연스럽게 한 단계만 구체화합니다.
11. 감정 반응은 최대 1문장, 25자 안팎으로 짧게 합니다.
12. 상담, 치료, 진단처럼 들리는 위로는 하지 않습니다.
13. 전체 출력 질문은 "짧은 반응 + 질문" 형태여도 되지만, 합쳐서 1~2문장 이내로 유지합니다.

좋은 예:
- 좋으셨겠어요. 그때 어떤 장면이 제일 기억나세요?
- 아이고, 불편하셨겠어요. 그때는 어디에 계셨어요?
- 그러셨군요. 그 이야기를 떠올리면 어떤 모습이 먼저 생각나세요?
- 그때 어디에서 있었던 일인지 기억나세요?
- 그분과 함께했던 장면 중에 먼저 떠오르는 게 있으세요?
- 그 음식을 누구와 같이 드셨던 기억이 있으세요?

나쁜 예:
- 오늘 하루 중 기억나는 순간이 있으세요?
- 혹시 치매 검사를 받아보신 적 있으세요?
- 아까 말씀하신 정답이 맞나요?
- 많이 힘드셨겠지만 앞으로는 괜찮아질 거예요.
- 그건 상담을 받아보시는 게 좋겠어요.

출력 형식:
{{"nextQuestion":"질문","shouldChangeTopic":false,"reason":"짧은 이유"}}
""".strip()

    try:
        response = _get_client().chat.completions.create(
            model=GPT_MODEL,
            messages=[
                {
                    "role": "system",
                    "content": "한국어 말동무 AI입니다. 반드시 JSON만 출력합니다.",
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
            temperature=0.35,
        )

        content = response.choices[0].message.content
        parsed = _extract_json_object(content)
        question = clean_text(parsed.get("nextQuestion", ""))
        should_change_topic = bool(parsed.get("shouldChangeTopic", False))
        reason = clean_text(parsed.get("reason", ""))

        if is_weak_free_talk_answer(conversation_history[-1] if conversation_history else ""):
            should_change_topic = True

        if question in {clean_text(item) for item in previous_questions}:
            return {
                "nextQuestion": fallback_question,
                "shouldChangeTopic": should_change_topic,
                "reason": "repeated_question",
            }

        if is_similar_to_previous_question(question, previous_questions):
            return {
                "nextQuestion": fallback_question,
                "shouldChangeTopic": should_change_topic,
                "reason": "similar_question",
            }

        question = build_fallback_with_empathy(
            question,
            conversation_history,
        )

        if is_safe_followup_question(question):
            return {
                "nextQuestion": question,
                "shouldChangeTopic": should_change_topic,
                "reason": reason,
            }

    except Exception:
        return {
            "nextQuestion": fallback_question,
            "shouldChangeTopic": is_weak_free_talk_answer(
                conversation_history[-1] if conversation_history else ""
            ),
            "reason": "llm_failed",
        }

    return {
        "nextQuestion": fallback_question,
        "shouldChangeTopic": is_weak_free_talk_answer(
            conversation_history[-1] if conversation_history else ""
        ),
        "reason": "unsafe_question",
    }
