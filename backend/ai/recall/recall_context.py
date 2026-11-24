import re

from recall.memory_event import infer_answer_type
from recall.memory_evidence_extractor import extract_memory_clues


def recall_context_prefix(candidate: dict) -> str:
    """Use another grounded slot to identify the event without revealing the target."""
    answer_type = str(candidate.get("answerType") or "").upper()
    answer = re.sub(r"\s+", "", str(candidate.get("answerValue") or ""))
    evidence = candidate.get("evidence") or []
    source = " ".join(str(item.get("sourceText") or "") for item in evidence)
    normalized_source = re.sub(r"\s+", "", source)
    values = candidate.get("answerValues") or {}
    local_values: dict[str, set[str]] = {}
    for clue in extract_memory_clues(str(candidate.get("sourceText") or ""), answer_type):
        local_values.setdefault(clue["answerType"], set()).add(clue["answerValue"])
    for slot in ("TIME", "ACTIVITY", "FOOD", "MEDIA", "OBJECT", "PERSON", "PLACE"):
        if slot == answer_type:
            continue
        grounded = []
        for value in values.get(slot, []):
            value = str(value).strip()
            normalized = re.sub(r"\s+", "", value)
            if not normalized or normalized not in normalized_source:
                continue
            if answer and (answer in normalized or normalized in answer):
                continue
            if value not in grounded:
                grounded.append(value)
        local = [value for value in grounded if value in local_values.get(slot, set())]
        choices = local or grounded
        # Multiple event details are not interchangeable merely because they
        # share an episode. Prefer this target's own clause; otherwise abstain.
        if len(choices) == 1:
            if slot == "PLACE" and answer_type == "FOOD":
                return f"아까 {choices[0]}에서 드셨다고 했는데, "
            return f"아까 말씀하신 {choices[0]} 말인데요, "
    return ""


def clarify_recall_question(question: dict, question_text: str, records: list[dict]) -> str:
    source = next((row for row in records if row.get("answerRole") == "INITIAL"
                   and str(row.get("recallQuestionId")) == str(question.get("questionId"))), {})
    episode_id = source.get("episodeId")
    evidence = [row for row in records if episode_id and row.get("episodeId") == episode_id]
    if not evidence and source:
        evidence = [source]
    values: dict[str, list[str]] = {}
    for row in evidence:
        for clue in extract_memory_clues(str(row.get("transcriptText") or "")):
            values.setdefault(clue["answerType"], []).append(clue["answerValue"])
    prefix = recall_context_prefix({
        "answerType": infer_answer_type(question_text).value,
        "answerValue": question.get("expectedAnswer") or "",
        "sourceText": source.get("transcriptText") or "",
        "answerValues": values,
        "evidence": [{"sourceText": row.get("transcriptText")} for row in evidence],
    })
    if prefix and not question_text.startswith(prefix):
        return prefix + question_text
    return "질문을 다시 말씀드릴게요. " + question_text
