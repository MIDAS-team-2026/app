import re
from enum import Enum


class RecallResponseIntent(str, Enum):
    ANSWER = "ANSWER"
    CLARIFY = "CLARIFY"
    DISPUTE = "DISPUTE"


def classify_recall_response(text: str) -> RecallResponseIntent:
    """Route assessment consent/understanding before treating text as evidence."""
    normalized = " ".join(str(text or "").split())
    # Require a direct objection to the assistant's attribution, not a negated event.
    if re.match(
        r"^(?:아니[요요, ]*\s*)?(?:저는?\s*|나는?\s*|내가\s*|제가\s*)?"
        r"(?:그렇게\s*(?:말|말씀|얘기|이야기)|그런\s*(?:말|얘기|이야기)(?:을|는)?)\s*"
        r"(?:말(?:을)?\s*|말씀(?:을)?\s*|얘기(?:를)?\s*|이야기(?:를)?\s*)?"
        r"(?:안\s*(?:했|드렸)|한\s*적(?:이|은)?\s*없|없)",
        normalized,
    ):
        return RecallResponseIntent.DISPUTE
    if is_clarification_request(normalized) or is_repeat_reminder(normalized):
        return RecallResponseIntent.CLARIFY
    return RecallResponseIntent.ANSWER


def conversation_control(text: str) -> str | None:
    normalized = re.sub(r"[\s.,!?]+", "", str(text or ""))
    if re.fullmatch(
        r"(?:이제|오늘은|잠깐|좀)?(?:그만(?:할래요?|할게요?|하자|해(?:요|주세요)?|하고싶(?:어(?:요)?|네요))?|"
        r"쉬(?:자|고싶어(?:요)?|고싶네요)|대화(?:를)?(?:그만|끝낼(?:래|게)(?:요)?|종료)|여기까지)", normalized,
    ):
        return "PAUSE"
    if re.fullmatch(
        r"(?:이(?:질문|이야기|얘기)(?:은|는)?|그건)?(?:넘어가(?:자|요|주세요)?|"
        r"건너뛰(?:자|어|어요|어주세요)|말하기싫(?:어|어요))|"
        r"다른(?:이야기|얘기|주제|질문)(?:로)?(?:하자|해요|해줘|해주세요|넘어가자)", normalized,
    ):
        return "SKIP"
    return None


def is_acknowledgement(text: str) -> bool:
    normalized = re.sub(r"[\s.,!?]+", "", str(text or ""))
    return normalized in {"응", "네", "예", "맞아", "맞아요", "맞습니다", "그래", "그래요", "그렇지", "그렇죠"}


def is_greeting(text: str) -> bool:
    normalized = re.sub(r"[\s.,!?]+", "", str(text or ""))
    return normalized in {"안녕", "안녕하세요", "안녕하십니까", "반가워", "반가워요", "반갑습니다"}


def is_repeat_reminder(text: str) -> bool:
    normalized = re.sub(r"\s+", "", str(text or ""))
    return bool(re.search(
        r"(?:말했잖|말씀드렸잖|얘기했잖|왜또물|또물어보|"
        r"(?:아까|이미|방금).*(?:(?:다고|라고)(?:말|얘기)?했(?:던|잖|는데)|말했|말씀드렸))",
        normalized,
    ))


def is_clarification_request(text: str) -> bool:
    normalized = " ".join(str(text or "").split())
    return bool(re.search(
        r"(?:(?:무슨|어떤)\s*(?:말|뜻|질문|이야기|얘기)|"
        r"(?:다시|한\s*번\s*더)\s*(?:말씀|말해|설명)|"
        r"(?:질문|말씀|말)을?\s*(?:잘\s*)?(?:못\s*들|이해.*(?:안|못)))",
        normalized,
    ))
