from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from recall.memory_event import (
    MemoryCandidateDraft,
    MemoryEvidence,
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
