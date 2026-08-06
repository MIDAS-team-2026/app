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

    def to_dict(self) -> dict:
        return {
            "candidates": [
                {
                    "topic": candidate.topic,
                    "sourceRecordIds": list(candidate.source_record_ids),
                    "evidence": [
                        {
                            "sourceRecordId": item.source_record_id,
                            "sourceText": item.text,
                            "answerType": item.answer_type.value,
                            "answerValue": item.answer_value,
                            "qualityScore": item.quality_score,
                        }
                        for item in candidate.evidence
                    ],
                    "recallTarget": {
                        "sourceRecordId": candidate.recall_target.source_record_id,
                        "sourceText": candidate.recall_target.text,
                        "answerType": candidate.recall_target.answer_type.value,
                        "answerValue": candidate.recall_target.answer_value,
                        "qualityScore": candidate.recall_target.quality_score,
                    },
                    "answerValues": {
                        key: list(values)
                        for key, values in candidate.answer_values.items()
                    },
                    "qualityScore": candidate.quality_score,
                }
                for candidate in self.candidates
            ],
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
        min_quality_score=min_quality_score,
        max_candidates=max_candidates,
    )

    return MemoryCandidateSelection(
        candidates=tuple(candidates),
        rejected=tuple(rejected),
    )
