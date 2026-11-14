import re
from dataclasses import dataclass

from recall.episode_context import active_episode_records
from recall.memory_event import MemoryAnswerType, infer_answer_type, is_meaningful_recall_value
from recall.memory_evidence_extractor import extract_memory_clues
from recall.conversation_recall_generator import _keyword_occurs_in_text


REFERENCE_TYPES = {"그거": None, "그것": None, "거기": "PLACE", "그때": "TIME", "그분": "PERSON"}


def short_reference(text: str) -> str | None:
    normalized = re.sub(r"[\s.,!?]+", "", text)
    match = re.fullmatch(r"(그거|그것|거기|그때|그분)(?:요|야|이야|예요|이에요)?", normalized)
    return match.group(1) if match else None


def declines_detail(text: str) -> bool:
    return re.sub(r"[\s.,!?]+", "", text) in {"그냥", "그냥요", "모르겠어", "잘모르겠어", "모르겠어요", "잘모르겠어요"}


@dataclass(frozen=True)
class ReferenceResolution:
    source_text: str = ""
    clarified_before: bool = False


def resolve_short_reference(
    records: list[dict], text: str, preferred_type: MemoryAnswerType = MemoryAnswerType.UNKNOWN,
) -> ReferenceResolution:
    marker = short_reference(text)
    active = active_episode_records(records)
    previous = active[:-1] if active and str(active[-1].get("transcriptText") or "") == text else active
    clarified = any(row.get("conversationStage") == "CLARIFY" for row in previous)
    if not marker or not previous:
        return ReferenceResolution(clarified_before=clarified)
    question = str(previous[-1].get("aiReplyText") or "")
    slot = REFERENCE_TYPES[marker] or infer_answer_type(question).value
    if slot == "UNKNOWN":
        slot = preferred_type.value
    candidates = {}
    for row in previous:
        source = str(row.get("transcriptText") or "")
        # Do not label an arbitrary noun as a place/person just because the
        # question asks for one. These roles need explicit source evidence.
        preferred = MemoryAnswerType.UNKNOWN if slot in {"PLACE", "PERSON", "TIME"} else MemoryAnswerType(slot)
        for clue in extract_memory_clues(source, preferred):
            if not is_meaningful_recall_value(clue["answerType"], clue["answerValue"]):
                continue
            if slot != "UNKNOWN" and clue["answerType"] != slot:
                continue
            candidates.setdefault(clue["answerValue"], source)
    mentioned = {value: source for value, source in candidates.items() if _keyword_occurs_in_text(value, question)}
    grounded = mentioned or candidates
    return ReferenceResolution(next(iter(grounded.values())) if len(grounded) == 1 else "", clarified)
