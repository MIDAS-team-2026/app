"""Persisted outgoing question identity, with legacy text-only compatibility."""
import re


def normalized_question(text: str) -> str:
    return re.sub(r"[\s.,!?]+", "", str(text or ""))


def presented_question_matches(record: dict, question_id: int, text: str) -> bool:
    presented_id = record.get("presentedRecallQuestionId")
    if presented_id is not None:
        return str(presented_id) == str(question_id)
    normalized = normalized_question(text)
    return bool(normalized and normalized_question(record.get("aiReplyText")).endswith(normalized))
