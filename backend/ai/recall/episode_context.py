from __future__ import annotations


CONTROL_STAGES = frozenset({"PAUSED", "RECALL_SKIPPED", "RECALL_CLARIFY", "TOPIC_SKIPPED"})


def ordered_records(records: list[dict] | None) -> list[dict]:
    return sorted(
        records or [],
        key=lambda row: int(row.get("turnOrder") or row.get("recordId") or 0),
    )


def records_through_current(records: list[dict] | None, record_id: int) -> list[dict]:
    """Keep a delayed worker from consuming answers that arrived after its turn."""
    ordered = ordered_records(records)
    positions = [i for i, row in enumerate(ordered) if row.get("recordId") == record_id]
    if len(positions) != 1:
        return []
    return ordered[:positions[0] + 1]


def episode_segments(records: list[dict] | None) -> list[list[dict]]:
    """Shared boundaries; outgoing OPEN applies to the following answer."""
    segments: list[list[dict]] = []
    active: list[dict] = []
    previous: dict = {}
    active_id = ""
    for row in ordered_records(records):
        role = str(row.get("answerRole") or "").upper()
        if role in {"FIXED", "RECALL"} or row.get("conversationStage") in CONTROL_STAGES:
            active = []
            active_id = ""
            previous = row
            continue

        if str(previous.get("conversationStage") or "").upper() == "OPEN":
            active = []
            active_id = ""
        current_id = str(row.get("episodeId") or "")
        if current_id and active_id and current_id != active_id:
            active = []
            active_id = ""
        if not active:
            segments.append(active)
        active.append(row)
        active_id = current_id or active_id
        previous = row
    return segments


def active_episode_records(records: list[dict] | None) -> list[dict]:
    ordered = ordered_records(records)
    if not ordered or str(ordered[-1].get("answerRole") or "").upper() in {"FIXED", "RECALL"} or ordered[-1].get("conversationStage") in CONTROL_STAGES:
        return []
    segments = episode_segments(ordered)
    return segments[-1] if segments else []


def episode_identity(
    records: list[dict] | None, session_id: int, record_id: int, topic: str,
) -> str:
    active = active_episode_records(records_through_current(records, record_id))
    current = next(
        (row for row in active if int(row.get("recordId") or 0) == record_id),
        None,
    )
    if current and current.get("episodeId"):
        return str(current["episodeId"])
    # Missing metadata does not split a segment, but a known different topic does.
    for previous in reversed(active[:-1]):
        previous_topic = previous.get("conversationTopic")
        if previous_topic and previous_topic != topic:
            break
        if previous.get("episodeId"):
            if previous_topic == topic:
                return str(previous["episodeId"])
            break
    return f"S{session_id}-R{record_id}-{topic}"
