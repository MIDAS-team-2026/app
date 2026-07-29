from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import Enum
from typing import Optional


class MemoryAnswerType(str, Enum):
    PERSON = "PERSON"
    PLACE = "PLACE"
    TIME = "TIME"
    FOOD = "FOOD"
    MEDIA = "MEDIA"
    ACTIVITY = "ACTIVITY"
    OBJECT = "OBJECT"
    UNKNOWN = "UNKNOWN"


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
