from __future__ import annotations

from datetime import datetime

from recall.episode_context import ordered_records
from recall.conversation_policy import MAX_MEMORY_WAIT_SECONDS
from recall.recall_corrections import retracted_before_presentation
from recall.recall_presentation import normalized_question, presented_question_matches


def session_recall_history(records: list[dict], questions: list[dict]) -> dict:
    """Previous sessions' wording is not a duplicate of this session's event."""
    ids = {row.get("recallQuestionId") for row in records
           if row.get("answerRole") in {"INITIAL", "RECALL"}}
    ids.update(row.get("presentedRecallQuestionId") for row in records)
    ids.discard(None)
    return {
        "previous_questions": [q["questionText"] for q in questions
                               if q.get("questionId") in ids and q.get("questionText")],
        "used_memory_points": [row["transcriptText"] for row in records
                               if row.get("answerRole") == "INITIAL" and row.get("transcriptText")],
    }


def turn_position(records: list[dict], record_id: int) -> int:
    for index, row in enumerate(ordered_records(records), start=1):
        if int(row.get("recordId") or 0) == int(record_id):
            return int(row.get("turnOrder") or index)
    return 0


def recall_is_due(question: dict, current_turn: int) -> bool:
    status = str(question.get("recallStatus") or "").upper()
    if status not in {"", "PENDING_RECALL", "ACTIVE_RECALL"}:
        return False
    due = question.get("askAfterTurn")
    if due is None:
        return True
    try:
        return bool(current_turn and current_turn >= int(due))
    except (TypeError, ValueError):
        return False


def memory_wait_turns(records: list[dict], source_record_ids: tuple[int, ...]) -> int:
    """Count later free-talk answers from the newest evidence in one memory."""
    ordered = ordered_records(records)
    if not source_record_ids:
        return 0
    positions = {row.get("recordId"): index for index, row in enumerate(ordered)}
    if any(record_id not in positions for record_id in source_record_ids):
        return 0
    newest = max(positions[record_id] for record_id in source_record_ids)
    return sum(
        1 for row in ordered[newest + 1:]
        if str(row.get("transcriptText") or "").strip()
        and not row.get("answerRole")
        and row.get("conversationStage") not in {
            "PAUSED", "RECALL_CLARIFY", "RECALL_SKIPPED", "CLARIFY"
        }
    )


def scheduled_recall_turn(current_turn: int, *, prior_wait_turns: int = 0,
                          prior_wait_seconds: float | None = None) -> int:
    """Credit elapsed free talk, while keeping the creation reply conversational."""
    if prior_wait_seconds is not None and prior_wait_seconds >= MAX_MEMORY_WAIT_SECONDS:
        return current_turn
    return current_turn + max(1, 2 - max(0, prior_wait_turns))


def memory_wait_seconds(records: list[dict], source_record_ids: tuple[int, ...]) -> float | None:
    """Read persisted timestamps only; absent or inconsistent clocks stay unknown."""
    ordered = ordered_records(records)
    if not ordered or not source_record_ids:
        return None
    by_id = {row.get("recordId"): row for row in ordered}
    try:
        current = datetime.fromisoformat(str(ordered[-1]["recordedAt"]).replace("Z", "+00:00"))
        sources = [datetime.fromisoformat(str(by_id[record_id]["recordedAt"]).replace("Z", "+00:00"))
                   for record_id in source_record_ids]
        elapsed = [(current - source).total_seconds() for source in sources]
    except (KeyError, TypeError, ValueError, OverflowError):
        return None
    return min(elapsed) if all(value >= 0 for value in elapsed) else None


def select_pending_recall(
    records: list[dict], current_record_id: int, questions: list[dict] | None,
) -> int | None:
    ordered = ordered_records(records)
    current_index = next(
        (i for i, row in enumerate(ordered) if int(row.get("recordId") or 0) == int(current_record_id)),
        None,
    )
    if current_index is None:
        return None
    history = ordered[:current_index]
    completed = {
        int(row["recallQuestionId"])
        for row in ordered[:current_index + 1]
        if row.get("recallQuestionId") is not None and row.get("answerRole") == "RECALL"
    }
    pending = list(dict.fromkeys(
        int(row["recallQuestionId"])
        for row in history
        if row.get("answerRole") == "INITIAL" and row.get("recallQuestionId") is not None
        and int(row["recallQuestionId"]) not in completed
    ))
    metadata = {}
    for question in questions or []:
        if question.get("questionId") is None:
            continue
        question_id = int(question["questionId"])
        if question_id in metadata and question_id in pending:
            raise ValueError("ambiguous_pending_recall_question")
        metadata[question_id] = question
    if any(question_id not in metadata for question_id in pending):
        raise ValueError("unavailable_pending_recall_question")
    source_positions = {int(row["recallQuestionId"]): index for index, row in enumerate(history)
                        if row.get("answerRole") == "INITIAL" and row.get("recallQuestionId") is not None}
    pending = [qid for qid in pending if not retracted_before_presentation(
        ordered[:current_index + 1], metadata.get(qid, {}), source_positions[qid])]
    skipped = set()
    for index, row in enumerate(history):
        if not index or row.get("conversationStage") not in {"RECALL_SKIPPED", "PAUSED"}:
            continue
        for qid in pending:
            if presented_question_matches(history[index - 1], qid, metadata.get(qid, {}).get("questionText")):
                skipped.add(qid)
    pending = [qid for qid in pending if qid not in skipped]
    pending = [qid for qid in pending if str(metadata.get(qid, {}).get("recallStatus") or "").upper()
               in {"", "PENDING_RECALL", "ACTIVE_RECALL"}]
    previous = history[-1] if history else {}
    explicit_id = previous.get("presentedRecallQuestionId")
    if explicit_id is not None:
        current = ordered[current_index]
        if current.get("answerRole") == "RECALL" and str(current.get("recallQuestionId")) == str(explicit_id):
            return None
        matches = [qid for qid in pending if str(qid) == str(explicit_id)]
        if len(matches) != 1 or matches[0] not in metadata:
            raise ValueError("unavailable_presented_recall_question")
        return matches[0]

    # Finish the question the user actually heard before presenting any waiting item.
    presented = []
    for question_id in pending:
        if presented_question_matches(previous, question_id, metadata.get(question_id, {}).get("questionText")):
            presented.append(question_id)
    if len(presented) > 1:
        raise ValueError("ambiguous_presented_recall_question")
    if presented:
        return presented[0]

    current_turn = turn_position(ordered, current_record_id)
    for question_id in pending:
        question = metadata.get(question_id, {})
        if recall_is_due(question, current_turn):
            return question_id
    return None
