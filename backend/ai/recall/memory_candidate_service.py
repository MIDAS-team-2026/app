from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from recall.memory_event import (
    MemoryCandidateDraft,
    MemoryEvidence,
    parse_answer_type,
    group_memory_evidence,
    select_memory_candidate_drafts,
)


@dataclass(frozen=True)
class RejectedMemoryPayload:
    index: int
    reason: str


@dataclass(frozen=True)
class MemoryCandidateSelection:
    candidates: tuple[MemoryCandidateDraft, ...]
    rejected: tuple[RejectedMemoryPayload, ...]
    used_clue_ids: tuple[str, ...] = ()

    def to_dict(self) -> dict:
        used_clue_ids = set(self.used_clue_ids)
        candidates = []

        for candidate in self.candidates:
            selected_clue = candidate.select_recall_clue(used_clue_ids)
            recall_clues = [
                {
                    "clueId": item.clue_id,
                    "sourceRecordId": item.source_record_id,
                    "sourceText": item.source_text,
                    "answerType": item.clue.answer_type.value,
                    "answerValue": item.clue.answer_value,
                    "status": (
                        "USED"
                        if item.clue_id in used_clue_ids
                        else "AVAILABLE"
                    ),
                }
                for item in candidate.recall_clues
            ]
            candidates.append(
                {
                    "eventId": candidate.event_id,
                    "topic": candidate.topic,
                    "sourceRecordIds": list(candidate.source_record_ids),
                    "evidence": [
                        {
                            "sourceRecordId": item.source_record_id,
                            "sourceText": item.text,
                            **(
                                {"turnOrder": item.turn_order}
                                if item.turn_order is not None
                                else {}
                            ),
                            "answerType": item.answer_type.value,
                            "answerValue": item.answer_value,
                            "clues": [
                                {
                                    "answerType": clue.answer_type.value,
                                    "answerValue": clue.answer_value,
                                }
                                for clue in item.clues
                            ],
                            "qualityScore": item.quality_score,
                            "continuesPreviousEvent": item.continues_previous_event,
                            **(
                                {"isCorrection": True}
                                if item.is_correction
                                else {}
                            ),
                        }
                        for item in candidate.evidence
                    ],
                    "recallTarget": {
                        "sourceRecordId": candidate.recall_target.source_record_id,
                        "sourceText": candidate.recall_target.text,
                        **(
                            {"turnOrder": candidate.recall_target.turn_order}
                            if candidate.recall_target.turn_order is not None
                            else {}
                        ),
                        "answerType": candidate.recall_target.answer_type.value,
                        "answerValue": candidate.recall_target.answer_value,
                        "clues": [
                            {
                                "answerType": clue.answer_type.value,
                                "answerValue": clue.answer_value,
                            }
                            for clue in candidate.recall_target.clues
                        ],
                        "qualityScore": candidate.recall_target.quality_score,
                        "continuesPreviousEvent": (
                            candidate.recall_target.continues_previous_event
                        ),
                        **(
                            {"isCorrection": True}
                            if candidate.recall_target.is_correction
                            else {}
                        ),
                    },
                    "recallClue": {
                        "clueId": selected_clue.clue_id,
                        "sourceRecordId": selected_clue.source_record_id,
                        "sourceText": selected_clue.source_text,
                        "answerType": selected_clue.clue.answer_type.value,
                        "answerValue": selected_clue.clue.answer_value,
                    },
                    "recallClues": recall_clues,
                    "answerValues": {
                        key: list(values)
                        for key, values in candidate.answer_values.items()
                    },
                    "qualityScore": candidate.quality_score,
                }
            )

        return {
            "candidates": candidates,
            "rejected": [
                {
                    "index": item.index,
                    "reason": item.reason,
                }
                for item in self.rejected
            ],
        }


def validate_recall_candidate_contract(candidate: dict) -> None:
    """Reject a full recall candidate whose selected clue and event disagree."""
    if not isinstance(candidate, dict):
        raise TypeError("memory candidate must be a dictionary")

    context_fields = {
        "topic",
        "sourceRecordIds",
        "evidence",
        "recallClue",
        "answerValues",
    }
    present_context_fields = context_fields.intersection(candidate)

    # Keep compatibility with the earlier flat candidate contract.
    if not present_context_fields:
        return

    missing_fields = context_fields - candidate.keys()
    if missing_fields:
        raise ValueError(
            "memory candidate context is incomplete: "
            + ", ".join(sorted(missing_fields))
        )

    topic = str(candidate.get("topic") or "").strip().upper()
    event_id = str(candidate.get("eventId") or "").strip()

    if topic in {"", "GENERAL", "UNKNOWN", "UNGROUPED"}:
        raise ValueError("memory candidate topic is invalid")

    try:
        source_record_ids = tuple(
            int(record_id)
            for record_id in candidate.get("sourceRecordIds") or ()
        )
    except (TypeError, ValueError) as error:
        raise ValueError("sourceRecordIds must contain integers") from error

    if (
        not source_record_ids
        or any(record_id <= 0 for record_id in source_record_ids)
        or len(source_record_ids) != len(set(source_record_ids))
    ):
        raise ValueError("sourceRecordIds must be unique positive integers")

    evidence_payloads = candidate.get("evidence")

    if not isinstance(evidence_payloads, list) or not evidence_payloads:
        raise ValueError("memory candidate evidence is required")

    evidence = tuple(
        MemoryEvidence.from_payload({**item, "topic": topic})
        for item in evidence_payloads
    )
    draft = MemoryCandidateDraft(topic=topic, evidence=evidence)

    if draft.event_id != event_id:
        raise ValueError("eventId does not match the candidate evidence")

    if draft.source_record_ids != source_record_ids:
        raise ValueError("sourceRecordIds do not match the candidate evidence")

    selected = candidate.get("recallClue")

    if not isinstance(selected, dict):
        raise ValueError("recallClue must be a dictionary")

    try:
        selected_record_id = int(candidate.get("sourceRecordId") or 0)
        nested_record_id = int(selected.get("sourceRecordId") or 0)
    except (TypeError, ValueError) as error:
        raise ValueError("selected sourceRecordId must be an integer") from error

    selected_text = " ".join(str(candidate.get("sourceText") or "").split())
    selected_type = parse_answer_type(candidate.get("answerType"))
    selected_value = " ".join(str(candidate.get("answerValue") or "").split())
    nested_type = parse_answer_type(selected.get("answerType"))
    nested_value = " ".join(str(selected.get("answerValue") or "").split())
    nested_text = " ".join(str(selected.get("sourceText") or "").split())

    if (
        selected_record_id != nested_record_id
        or selected_text != nested_text
        or selected_type != nested_type
        or selected_value != nested_value
    ):
        raise ValueError("flat candidate fields do not match recallClue")

    matching_clues = [
        clue
        for clue in draft.recall_clues
        if (
            clue.source_record_id == selected_record_id
            and clue.source_text == selected_text
            and clue.clue.answer_type == selected_type
            and clue.clue.answer_value == selected_value
        )
    ]

    if not matching_clues:
        raise ValueError("recallClue is not grounded in the candidate evidence")

    clue_id = str(selected.get("clueId") or "").strip()
    if clue_id and all(clue.clue_id != clue_id for clue in matching_clues):
        raise ValueError("recallClue clueId does not match the candidate evidence")

    raw_answer_values = candidate.get("answerValues")
    if not isinstance(raw_answer_values, dict):
        raise ValueError("answerValues must be a dictionary")

    normalized_answer_values = {
        str(answer_type or "").strip().upper(): tuple(
            " ".join(str(value or "").split())
            for value in values or ()
            if " ".join(str(value or "").split())
        )
        for answer_type, values in raw_answer_values.items()
    }

    if normalized_answer_values != draft.answer_values:
        raise ValueError("answerValues do not match the candidate evidence")


def build_memory_candidate_selection(
    payloads: Iterable[dict],
    *,
    used_source_record_ids: Iterable[int] = (),
    used_event_ids: Iterable[str] = (),
    used_clue_ids: Iterable[str] = (),
    min_quality_score: int = 40,
    max_candidates: int = 3,
    max_record_gap: int = 3,
) -> MemoryCandidateSelection:
    evidence = []
    rejected = []

    for index, payload in enumerate(payloads):
        try:
            evidence.append(MemoryEvidence.from_payload(payload))
        except (TypeError, ValueError) as error:
            rejected.append(
                RejectedMemoryPayload(
                    index=index,
                    reason=str(error),
                )
            )

    drafts = group_memory_evidence(
        evidence,
        max_record_gap=max_record_gap,
    )
    candidates = select_memory_candidate_drafts(
        drafts,
        used_source_record_ids=used_source_record_ids,
        used_event_ids=used_event_ids,
        used_clue_ids=used_clue_ids,
        min_quality_score=min_quality_score,
        max_candidates=max_candidates,
        require_confirmed_value=True,
    )

    return MemoryCandidateSelection(
        candidates=tuple(candidates),
        rejected=tuple(rejected),
        used_clue_ids=tuple(used_clue_ids),
    )
