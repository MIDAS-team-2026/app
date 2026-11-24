"""Conservative retractions of not-yet-presented recall facts.

Keep original transcripts and DB rows intact. Only explicit replacement of a
named answer can suppress an old question; vague corrections are not guessed.
"""
import re
from recall.recall_presentation import presented_question_matches


def explicitly_retracts(text: str, value: str) -> bool:
    value = " ".join(str(value or "").split())
    if not value:
        return False
    pattern = rf"(?<![가-힣A-Za-z0-9]){re.escape(value)}(?:이|가|은|는)?\s*(?:아니(?:라|고|야|에요|예요|었)|말고)"
    if not re.search(pattern, str(text or "")):
        return False
    from recall.conversation_recall_generator import score_recall_memory_candidate
    reasons = set(score_recall_memory_candidate(text).get("reasons") or ())
    return not reasons.intersection({"future_or_wish_expression", "uncertain_memory"})


def retracted_before_presentation(records: list[dict], question: dict, source_index: int) -> bool:
    question_text = re.sub(r"[\s.,!?]+", "", str(question.get("questionText") or ""))
    if not question_text:
        return False
    keywords = question.get("keywords") or []
    values = [question.get("expectedAnswer")]
    if isinstance(keywords, (list, tuple)):
        values.extend(keywords)
    for index, row in enumerate(records[source_index:], start=source_index):
        # A user's text precedes the outgoing reply stored on the same row.
        if index > source_index and row.get("answerRole") not in {"FIXED", "RECALL"}:
            if any(explicitly_retracts(str(row.get("transcriptText") or ""), value) for value in values):
                return True
        if presented_question_matches(row, question.get("questionId"), question.get("questionText")):
            break
    return False
