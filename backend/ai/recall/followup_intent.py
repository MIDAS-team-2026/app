from __future__ import annotations

from dataclasses import dataclass
import re

from recall.episode_context import active_episode_records, ordered_records
from recall.memory_event import MemoryAnswerType, infer_answer_type
from recall.memory_evidence_extractor import extract_memory_clues, is_context_only_place


def infer_followup_target(question: str) -> str:
    if any(cue in question for cue in ("이유", "왜 ", "어떤 점 때문에")):
        return "REASON"
    target = infer_answer_type(question).value
    if target == "UNKNOWN" and re.search(r"(?:곳|장소)(?:이|가|은|는|을|를)\s", question):
        return "PLACE"
    return target


def render_event_followups(missing_types: tuple[str, ...], content_questions: list[str]) -> list[str]:
    detail_questions = {
        "TIME": "그때는 언제쯤이었어요?",
        "PLACE": "그때는 어디에 계셨어요?",
        "PERSON": "그때 누구와 함께 계셨어요?",
    }
    questions = []
    for slot in missing_types:
        if slot in detail_questions:
            questions.append(detail_questions[slot])
        else:
            questions.extend(question for question in content_questions if infer_answer_type(question).value == slot)
    return list(dict.fromkeys(questions))


@dataclass(frozen=True)
class FollowupIntentState:
    known_types: frozenset[str]
    attempted_types: frozenset[str]

    @property
    def has_sufficient_detail(self) -> bool:
        factual_types = {slot.value for slot in MemoryAnswerType} - {"UNKNOWN"}
        return len(self.known_types & factual_types) >= 3

    def allows(self, question: str) -> bool:
        target = infer_followup_target(question)
        return target not in self.known_types and target not in self.attempted_types

    def preserves_target(self, planned: str, rendered: str) -> bool:
        return self.allows(rendered) and infer_followup_target(planned) == infer_followup_target(rendered)


def build_followup_intent_state(
    records: list[dict] | None,
    history: list[str],
    preferred_type: MemoryAnswerType,
    generic_values: dict[str, set[str]],
) -> FollowupIntentState:
    """Track information within the active episode, not across all daily events."""
    ordered = ordered_records(records)
    active = active_episode_records(ordered)
    active_ids = {row.get("recordId") for row in active}
    known: set[str] = set()
    attempted: set[str] = set()

    def observe(text: str, answer_type: MemoryAnswerType) -> None:
        for clue in extract_memory_clues(text, answer_type):
            slot, value = clue["answerType"], clue["answerValue"]
            if slot == "PLACE" and is_context_only_place(value):
                continue
            if value and value not in generic_values.get(slot, set()):
                known.add(slot)

    for index, row in enumerate(ordered):
        if row.get("recordId") not in active_ids:
            continue
        question = str(ordered[index - 1].get("aiReplyText") or "") if index else ""
        target = infer_answer_type(question)
        # The first question may open the topic; only subsequent detail questions
        # consume an attempt within this episode.
        dialogue_target = infer_followup_target(question)
        if index and ordered[index - 1].get("recordId") in active_ids and dialogue_target != "UNKNOWN":
            attempted.add(dialogue_target)
        observe(str(row.get("transcriptText") or ""),
                preferred_type if target == MemoryAnswerType.UNKNOWN else target)

    # Do not reinterpret an answer to a person/time question as the topic's
    # primary type (for example, "alone" is not a food).
    if not any(str(row.get("transcriptText") or "").strip() for row in ordered):
        for text in history:
            observe(text, preferred_type)
    return FollowupIntentState(frozenset(known), frozenset(attempted))
