from __future__ import annotations

import hashlib
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


PERSON_QUESTION_CUES = (
    "누구",
    "누가",
    "누군가",
    "어느 분",
    "어떤 분",
    "어떤 사람",
    "분이 계",
)
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
    "유튜브에서",
    "넷플릭스에서",
    "어떤 영상",
    "무슨 영상",
    "라디오에서",
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
    "어떤 선물",
    "무슨 선물",
    "무엇을 샀",
    "뭘 샀",
    "무엇을 받",
    "뭘 받",
    "무엇을 주",
    "뭘 주",
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


def should_continue_memory_event(
    *,
    last_event_topic: str | None,
    detected_topic: str | None,
    question_topics: Iterable[str],
    answer_type: MemoryAnswerType | str,
    is_correction: bool = False,
    is_referential_followup: bool = False,
) -> bool:
    """Return True only when the current answer still describes the last event.

    A follow-up can legitimately change the answer slot while keeping the same
    event. For example, a PLACE event may be followed by a PERSON answer to
    "who went with you?". A completely new MEDIA answer to a MEAL follow-up,
    however, must start a new event.
    """
    last_topic = str(last_event_topic or "").strip().upper()
    current_topic = str(detected_topic or "").strip().upper()
    anchored_topics = {
        str(topic or "").strip().upper()
        for topic in question_topics
        if str(topic or "").strip()
    }

    if last_topic in {"", "GENERAL", "UNKNOWN", "UNGROUPED"}:
        return False

    answer_slot_topic = infer_topic(parse_answer_type(answer_type))
    compatible_topics = {last_topic, answer_slot_topic}

    if is_correction:
        return (
            current_topic == last_topic
            or current_topic in anchored_topics
        )

    if last_topic not in anchored_topics:
        if not is_referential_followup:
            return False

        return current_topic in compatible_topics or current_topic in anchored_topics

    return current_topic in compatible_topics


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
class MemoryClue:
    answer_type: MemoryAnswerType
    answer_value: str

    @classmethod
    def create(cls, answer_type, answer_value) -> "MemoryClue | None":
        parsed_type = parse_answer_type(answer_type)
        cleaned_value = " ".join(str(answer_value or "").split())

        if parsed_type == MemoryAnswerType.UNKNOWN or not cleaned_value:
            return None

        return cls(parsed_type, cleaned_value)


@dataclass(frozen=True)
class MemoryRecallClue:
    clue_id: str
    source_record_id: int
    source_text: str
    clue: MemoryClue


@dataclass(frozen=True)
class MemoryEvidence:
    source_record_id: int
    text: str
    topic: str
    turn_order: int | None = None
    answer_type: MemoryAnswerType = MemoryAnswerType.UNKNOWN
    answer_value: str = ""
    clues: tuple[MemoryClue, ...] = ()
    is_correction: bool = False
    quality_score: int = 0
    continues_previous_event: bool = False

    @classmethod
    def create(
        cls,
        *,
        source_record_id: int,
        text: str,
        topic: str,
        turn_order: int | None = None,
        answer_type: MemoryAnswerType | str = MemoryAnswerType.UNKNOWN,
        answer_value: str = "",
        clues: Iterable[MemoryClue | dict | tuple] = (),
        is_correction: bool = False,
        quality_score: int = 0,
        continues_previous_event: bool = False,
    ) -> "MemoryEvidence":
        record_id = int(source_record_id)
        cleaned_text = " ".join(str(text or "").split())

        if record_id <= 0:
            raise ValueError("sourceRecordId must be a positive integer")

        if not cleaned_text:
            raise ValueError("sourceText or transcriptText is required")

        parsed_type = parse_answer_type(answer_type)
        cleaned_value = " ".join(str(answer_value or "").split())
        normalized_clues = []

        if parsed_type != MemoryAnswerType.UNKNOWN and cleaned_value:
            normalized_clues.append(MemoryClue(parsed_type, cleaned_value))

        for clue in clues or ():
            if isinstance(clue, MemoryClue):
                normalized = clue
            elif isinstance(clue, dict):
                normalized = MemoryClue.create(
                    clue.get("answerType"),
                    clue.get("answerValue") or clue.get("answerKeyword"),
                )
            else:
                try:
                    normalized = MemoryClue.create(clue[0], clue[1])
                except (IndexError, TypeError):
                    normalized = None

            if normalized and normalized not in normalized_clues:
                normalized_clues.append(normalized)

        if normalized_clues and not cleaned_value:
            parsed_type = normalized_clues[0].answer_type
            cleaned_value = normalized_clues[0].answer_value

        return cls(
            source_record_id=record_id,
            text=cleaned_text,
            topic=str(topic or "GENERAL").strip().upper(),
            turn_order=(
                int(turn_order)
                if turn_order is not None and int(turn_order) > 0
                else None
            ),
            answer_type=parsed_type,
            answer_value=cleaned_value,
            clues=tuple(normalized_clues),
            is_correction=bool(is_correction),
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
            turn_order=payload.get("turnOrder"),
            answer_type=payload.get("answerType") or MemoryAnswerType.UNKNOWN,
            answer_value=(
                payload.get("answerKeyword")
                or payload.get("answerValue")
                or ""
            ),
            clues=payload.get("clues") or (),
            is_correction=payload.get("isCorrection", False),
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
    def event_id(self) -> str:
        return f"{self.topic}:{self.first_source_record_id}"

    @property
    def answer_values(self) -> dict[str, tuple[str, ...]]:
        values: dict[str, list[str]] = {}

        for item in self.evidence:
            if item.is_correction:
                for clue in item.clues:
                    values.pop(clue.answer_type.value, None)

            for clue in item.clues:
                key = clue.answer_type.value
                values.setdefault(key, [])

                if clue.answer_value not in values[key]:
                    values[key].append(clue.answer_value)

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
    def first_position(self) -> int:
        if not self.evidence:
            return 0

        first = self.evidence[0]
        return first.turn_order or first.source_record_id

    @property
    def recall_target(self) -> MemoryEvidence:
        typed_evidence = [
            item
            for item in self.evidence
            if (
                bool(item.clues)
            )
        ]
        candidates = typed_evidence or list(self.evidence)

        return max(
            candidates,
            key=lambda item: (
                item.is_correction,
                item.quality_score,
                len(item.answer_value),
                -item.source_record_id,
            ),
        )

    @property
    def recall_clue(self) -> MemoryClue:
        return self.select_recall_clue().clue

    @property
    def recall_clues(self) -> tuple[MemoryRecallClue, ...]:
        active_values = self.answer_values
        candidates = []
        seen = set()

        for item in reversed(self.evidence):
            for clue in item.clues:
                identity = (clue.answer_type, clue.answer_value)

                if identity in seen:
                    continue

                if clue.answer_value not in active_values.get(
                    clue.answer_type.value,
                    (),
                ):
                    continue

                digest = hashlib.sha1(
                    clue.answer_value.casefold().encode("utf-8")
                ).hexdigest()[:12]
                candidates.append(
                    MemoryRecallClue(
                        clue_id=(
                            f"{self.event_id}:{clue.answer_type.value}:{digest}"
                        ),
                        source_record_id=item.source_record_id,
                        source_text=item.text,
                        clue=clue,
                    )
                )
                seen.add(identity)

        target = self.recall_target
        candidates.sort(
            key=lambda candidate: (
                candidate.source_record_id == target.source_record_id
                and candidate.clue.answer_type == target.answer_type
                and candidate.clue.answer_value == target.answer_value,
                candidate.source_record_id,
            ),
            reverse=True,
        )
        return tuple(candidates)

    def select_recall_clue(
        self,
        used_clue_ids: Iterable[str] = (),
    ) -> MemoryRecallClue:
        used_ids = {
            str(clue_id or "").strip()
            for clue_id in used_clue_ids
            if str(clue_id or "").strip()
        }

        for candidate in self.recall_clues:
            if candidate.clue_id not in used_ids:
                return candidate

        raise ValueError("memory event has no available recall clue")

    def can_merge(self, item: MemoryEvidence, max_record_gap: int = 3) -> bool:
        if not self.evidence or self.topic in {"", "GENERAL", "UNKNOWN"}:
            return False

        if item.topic != self.topic:
            return False

        if not item.continues_previous_event and not item.is_correction:
            return False

        previous = self.evidence[-1]
        previous_position = previous.turn_order or previous.source_record_id
        current_position = item.turn_order or item.source_record_id
        record_gap = current_position - previous_position

        if not 0 < record_gap <= max_record_gap:
            return False

        if not item.clues:
            return True

        if item.is_correction:
            return True

        return True

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

    ordered_items = sorted(
        items,
        key=lambda current: (
            current.turn_order
            if current.turn_order is not None
            else current.source_record_id,
            current.source_record_id,
        ),
    )

    for item in ordered_items:
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
    if set(current.source_record_ids) & set(selected.source_record_ids):
        return True

    current_texts = {
        "".join(item.text.casefold().split())
        for item in current.evidence
    }
    selected_texts = {
        "".join(item.text.casefold().split())
        for item in selected.evidence
    }

    if current_texts & selected_texts:
        return True

    if current.topic != selected.topic:
        return False

    current_clues = {
        (answer_type, value.casefold())
        for answer_type, values in current.answer_values.items()
        for value in values
    }
    selected_clues = {
        (answer_type, value.casefold())
        for answer_type, values in selected.answer_values.items()
        for value in values
    }

    if not current_clues or not selected_clues:
        return False

    shared_clues = current_clues & selected_clues
    smaller_clue_count = min(len(current_clues), len(selected_clues))
    return (
        len(shared_clues) >= 2
        and len(shared_clues) == smaller_clue_count
    )


def select_memory_candidate_drafts(
    drafts: Iterable[MemoryCandidateDraft],
    *,
    used_source_record_ids: Iterable[int] = (),
    used_event_ids: Iterable[str] = (),
    used_clue_ids: Iterable[str] = (),
    min_quality_score: int = 40,
    max_candidates: int = 3,
    require_confirmed_value: bool = False,
) -> list[MemoryCandidateDraft]:
    used_ids = {int(record_id) for record_id in used_source_record_ids}
    used_events = {
        str(event_id or "").strip()
        for event_id in used_event_ids
        if str(event_id or "").strip()
    }
    used_clues = {
        str(clue_id or "").strip()
        for clue_id in used_clue_ids
        if str(clue_id or "").strip()
    }
    eligible = [
        draft
        for draft in drafts
        if (
            draft.topic not in {"", "GENERAL", "UNKNOWN"}
            and draft.quality_score >= min_quality_score
            and (
                not require_confirmed_value
                or any(
                    bool(item.clues)
                    for item in draft.evidence
                )
            )
            and not used_ids.intersection(draft.source_record_ids)
            and draft.event_id not in used_events
            and (
                not draft.recall_clues
                or any(
                    clue.clue_id not in used_clues
                    for clue in draft.recall_clues
                )
            )
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

    return sorted(selected, key=lambda draft: draft.first_position)
