from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import Enum
from typing import Iterable, Optional


class MemoryAnswerType(str, Enum):
    PERSON = "PERSON"
    PLACE = "PLACE"
    TIME = "TIME"
    FOOD = "FOOD"
    MEDIA = "MEDIA"
    ACTIVITY = "ACTIVITY"
    OBJECT = "OBJECT"
    UNKNOWN = "UNKNOWN"


def parse_answer_type(value) -> MemoryAnswerType:
    if isinstance(value, MemoryAnswerType):
        return value

    try:
        return MemoryAnswerType(str(value or "UNKNOWN").strip().upper())
    except ValueError:
        return MemoryAnswerType.UNKNOWN


PERSON_QUESTION_CUES = ("누구", "누가", "어느 분", "어떤 분", "어떤 사람")
PLACE_QUESTION_CUES = ("어디", "어느 곳", "어떤 곳", "장소")
TIME_QUESTION_CUES = ("언제", "몇 시", "몇시", "시간대")
FOOD_QUESTION_CUES = (
    "무엇을 드",
    "뭘 드",
    "어떤 음식",
    "무슨 음식",
    "드신 음식",
    "무엇을 먹",
    "뭘 먹",
    "어떤 걸 드",
    "무슨 걸 드",
)
MEDIA_QUESTION_CUES = (
    "무슨 방송",
    "어떤 방송",
    "무슨 프로그램",
    "어떤 프로그램",
    "무슨 노래",
    "어떤 노래",
    "어떤 음악",
    "어느 가수",
)
ACTIVITY_QUESTION_CUES = (
    "무엇을 하",
    "뭘 하",
    "어떤 일을",
    "어떤 활동",
)
OBJECT_QUESTION_CUES = (
    "어떤 물건",
    "무슨 물건",
    "무엇을 샀",
    "뭘 샀",
)


def infer_answer_type(question: str) -> MemoryAnswerType:
    question = str(question or "").strip()

    cue_groups = (
        (MemoryAnswerType.PERSON, PERSON_QUESTION_CUES),
        (MemoryAnswerType.PLACE, PLACE_QUESTION_CUES),
        (MemoryAnswerType.TIME, TIME_QUESTION_CUES),
        (MemoryAnswerType.FOOD, FOOD_QUESTION_CUES),
        (MemoryAnswerType.MEDIA, MEDIA_QUESTION_CUES),
        (MemoryAnswerType.ACTIVITY, ACTIVITY_QUESTION_CUES),
        (MemoryAnswerType.OBJECT, OBJECT_QUESTION_CUES),
    )

    for answer_type, cues in cue_groups:
        if any(cue in question for cue in cues):
            return answer_type

    return MemoryAnswerType.UNKNOWN


def infer_topic(
    answer_type: MemoryAnswerType,
    action: str = "",
) -> str:
    if answer_type == MemoryAnswerType.FOOD:
        return "MEAL"

    if answer_type != MemoryAnswerType.UNKNOWN:
        return answer_type.value

    return str(action or "GENERAL").upper()


@dataclass(frozen=True)
class MemoryEvent:
    source_text: str
    memory_point: str
    answer_keyword: str
    answer_type: MemoryAnswerType
    topic: str
    question: str
    action: str = ""
    source_record_id: Optional[int] = None
    person: Optional[str] = None
    place: Optional[str] = None
    time_expression: Optional[str] = None
    object_name: Optional[str] = None
    quality_score: int = 0
    used_for_recall: bool = False

    @classmethod
    def from_generation(
        cls,
        *,
        source_text: str,
        memory_point: str,
        answer_keyword: str,
        question: str,
        action: str = "",
        source_record_id: Optional[int] = None,
        quality_score: int = 0,
    ) -> "MemoryEvent":
        answer_type = infer_answer_type(question)

        return cls(
            source_text=str(source_text or "").strip(),
            memory_point=str(memory_point or "").strip(),
            answer_keyword=str(answer_keyword or "").strip(),
            answer_type=answer_type,
            topic=infer_topic(answer_type, action),
            question=str(question or "").strip(),
            action=str(action or "").strip(),
            source_record_id=source_record_id,
            person=(
                answer_keyword
                if answer_type == MemoryAnswerType.PERSON
                else None
            ),
            place=(
                answer_keyword
                if answer_type == MemoryAnswerType.PLACE
                else None
            ),
            time_expression=(
                answer_keyword
                if answer_type == MemoryAnswerType.TIME
                else None
            ),
            object_name=(
                answer_keyword
                if answer_type
                in {
                    MemoryAnswerType.FOOD,
                    MemoryAnswerType.MEDIA,
                    MemoryAnswerType.OBJECT,
                }
                else None
            ),
            quality_score=max(0, min(int(quality_score or 0), 100)),
        )

    def to_dict(self) -> dict:
        payload = asdict(self)
        payload["answer_type"] = self.answer_type.value

        return {
            "sourceText": payload["source_text"],
            "memoryPoint": payload["memory_point"],
            "answerKeyword": payload["answer_keyword"],
            "answerType": payload["answer_type"],
            "topic": payload["topic"],
            "question": payload["question"],
            "action": payload["action"],
            "sourceRecordId": payload["source_record_id"],
            "person": payload["person"],
            "place": payload["place"],
            "timeExpression": payload["time_expression"],
            "objectName": payload["object_name"],
            "qualityScore": payload["quality_score"],
            "usedForRecall": payload["used_for_recall"],
        }


@dataclass(frozen=True)
class MemoryEvidence:
    source_record_id: int
    text: str
    topic: str
    answer_type: MemoryAnswerType = MemoryAnswerType.UNKNOWN
    answer_value: str = ""
    quality_score: int = 0
    continues_previous_event: bool = False

    @classmethod
    def create(
        cls,
        *,
        source_record_id: int,
        text: str,
        topic: str,
        answer_type: MemoryAnswerType | str = MemoryAnswerType.UNKNOWN,
        answer_value: str = "",
        quality_score: int = 0,
        continues_previous_event: bool = False,
    ) -> "MemoryEvidence":
        record_id = int(source_record_id)
        cleaned_text = " ".join(str(text or "").split())

        if record_id <= 0:
            raise ValueError("sourceRecordId must be a positive integer")

        if not cleaned_text:
            raise ValueError("sourceText or transcriptText is required")

        return cls(
            source_record_id=record_id,
            text=cleaned_text,
            topic=str(topic or "GENERAL").strip().upper(),
            answer_type=parse_answer_type(answer_type),
            answer_value=" ".join(str(answer_value or "").split()),
            quality_score=max(0, min(int(quality_score or 0), 100)),
            continues_previous_event=bool(continues_previous_event),
        )

    @classmethod
    def from_payload(cls, payload: dict) -> "MemoryEvidence":
        if not isinstance(payload, dict):
            raise TypeError("memory evidence payload must be a dictionary")

        source_record_id = payload.get("sourceRecordId")
        text = payload.get("sourceText") or payload.get("transcriptText")

        if source_record_id is None:
            raise ValueError("sourceRecordId is required")

        return cls.create(
            source_record_id=source_record_id,
            text=text,
            topic=payload.get("topic") or "GENERAL",
            answer_type=payload.get("answerType") or MemoryAnswerType.UNKNOWN,
            answer_value=(
                payload.get("answerKeyword")
                or payload.get("answerValue")
                or ""
            ),
            quality_score=payload.get("qualityScore") or 0,
            continues_previous_event=payload.get("continuesPreviousEvent", False),
        )


@dataclass(frozen=True)
class MemoryCandidateDraft:
    topic: str
    evidence: tuple[MemoryEvidence, ...]

    @property
    def source_record_ids(self) -> tuple[int, ...]:
        return tuple(item.source_record_id for item in self.evidence)

    @property
    def answer_values(self) -> dict[str, tuple[str, ...]]:
        values: dict[str, list[str]] = {}

        for item in self.evidence:
            if not item.answer_value:
                continue

            key = item.answer_type.value
            values.setdefault(key, [])

            if item.answer_value not in values[key]:
                values[key].append(item.answer_value)

        return {key: tuple(items) for key, items in values.items()}

    @property
    def quality_score(self) -> int:
        if not self.evidence:
            return 0

        best_score = max(item.quality_score for item in self.evidence)
        detail_bonus = min(15, max(0, len(self.answer_values) - 1) * 5)
        return min(100, best_score + detail_bonus)

    @property
    def detail_count(self) -> int:
        return sum(len(values) for values in self.answer_values.values())

    @property
    def first_source_record_id(self) -> int:
        return self.source_record_ids[0] if self.source_record_ids else 0

    @property
    def recall_target(self) -> MemoryEvidence:
        typed_evidence = [
            item
            for item in self.evidence
            if (
                item.answer_type != MemoryAnswerType.UNKNOWN
                and item.answer_value
            )
        ]
        candidates = typed_evidence or list(self.evidence)

        return max(
            candidates,
            key=lambda item: (
                item.quality_score,
                len(item.answer_value),
                -item.source_record_id,
            ),
        )

    def can_merge(self, item: MemoryEvidence, max_record_gap: int = 3) -> bool:
        if not self.evidence or self.topic in {"", "GENERAL", "UNKNOWN"}:
            return False

        if item.topic != self.topic:
            return False

        if not item.continues_previous_event:
            return False

        record_gap = item.source_record_id - self.source_record_ids[-1]

        if not 0 < record_gap <= max_record_gap:
            return False

        if not item.answer_value:
            return not any(
                existing.answer_type == item.answer_type
                for existing in self.evidence
            )

        if item.answer_type == MemoryAnswerType.UNKNOWN:
            return False

        existing_values = self.answer_values.get(item.answer_type.value, ())
        return not existing_values or item.answer_value in existing_values

    def add(self, item: MemoryEvidence) -> "MemoryCandidateDraft":
        return MemoryCandidateDraft(
            topic=self.topic,
            evidence=(*self.evidence, item),
        )


def group_memory_evidence(
    items: Iterable[MemoryEvidence],
    max_record_gap: int = 3,
) -> list[MemoryCandidateDraft]:
    drafts: list[MemoryCandidateDraft] = []

    for item in sorted(items, key=lambda current: current.source_record_id):
        if drafts and drafts[-1].can_merge(item, max_record_gap=max_record_gap):
            drafts[-1] = drafts[-1].add(item)
            continue

        drafts.append(
            MemoryCandidateDraft(
                topic=item.topic,
                evidence=(item,),
            )
        )

    return drafts


def _is_duplicate_candidate(
    current: MemoryCandidateDraft,
    selected: MemoryCandidateDraft,
) -> bool:
    if current.topic != selected.topic:
        return False

    current_values = {
        value
        for values in current.answer_values.values()
        for value in values
    }
    selected_values = {
        value
        for values in selected.answer_values.values()
        for value in values
    }

    if current_values and selected_values:
        return bool(current_values & selected_values)

    current_texts = {item.text for item in current.evidence}
    selected_texts = {item.text for item in selected.evidence}
    return bool(current_texts & selected_texts)


def select_memory_candidate_drafts(
    drafts: Iterable[MemoryCandidateDraft],
    *,
    used_source_record_ids: Iterable[int] = (),
    min_quality_score: int = 40,
    max_candidates: int = 3,
) -> list[MemoryCandidateDraft]:
    used_ids = {int(record_id) for record_id in used_source_record_ids}
    eligible = [
        draft
        for draft in drafts
        if (
            draft.topic not in {"", "GENERAL", "UNKNOWN"}
            and draft.quality_score >= min_quality_score
            and not used_ids.intersection(draft.source_record_ids)
        )
    ]
    eligible.sort(
        key=lambda draft: (
            draft.quality_score,
            draft.detail_count,
            len(draft.evidence),
        ),
        reverse=True,
    )

    selected: list[MemoryCandidateDraft] = []

    for draft in eligible:
        if any(_is_duplicate_candidate(draft, item) for item in selected):
            continue

        selected.append(draft)

        if len(selected) >= max(0, int(max_candidates)):
            break

    return sorted(selected, key=lambda draft: draft.first_source_record_id)
