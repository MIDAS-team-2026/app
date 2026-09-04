from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class ConversationAction(str, Enum):
    OPEN_TOPIC = "OPEN_TOPIC"
    FOLLOW_UP = "FOLLOW_UP"
    CHANGE_TOPIC = "CHANGE_TOPIC"
    RESUME_AFTER_RECALL = "RESUME_AFTER_RECALL"


class RecallTimingAction(str, Enum):
    WAIT = "WAIT"
    ASK_RECALL = "ASK_RECALL"


MIN_MATURED_RECALL_CANDIDATES = 2


@dataclass(frozen=True)
class ConversationDecision:
    action: ConversationAction
    stage: str
    reason: str


@dataclass(frozen=True)
class RecallTimingDecision:
    action: RecallTimingAction
    reason: str


def decide_conversation_action(
    *,
    candidate_count: int,
    after_recall_answer: bool,
    should_change_topic: bool,
    needs_memory_detail: bool = False,
    has_followup_context: bool = False,
) -> ConversationDecision:
    """다음 대화 액션을 정한다.

    주제를 언제 바꿀지는 더 이상 정해진 턴 수로 강제하지 않는다. LLM이
    매 턴 대화 맥락을 보고 반환하는 shouldChangeTopic 신호(free_talk_question_generator)에
    맡기고, 여기서는 대화 흐름상의 상태 신호(회상 복귀/부정 행동/빈약한 답변)만 다룬다.
    """
    if after_recall_answer:
        return ConversationDecision(
            action=ConversationAction.RESUME_AFTER_RECALL,
            stage="DEEPEN",
            reason="recall_answer_completed",
        )

    if should_change_topic:
        return ConversationDecision(
            action=ConversationAction.CHANGE_TOPIC,
            stage="OPEN",
            reason="latest_answer_cannot_support_a_followup",
        )

    if needs_memory_detail:
        return ConversationDecision(
            action=ConversationAction.FOLLOW_UP,
            stage="ANCHOR",
            reason="request_one_grounded_memory_detail",
        )

    if not has_followup_context:
        return ConversationDecision(
            action=ConversationAction.OPEN_TOPIC,
            stage="DEEPEN",
            reason="latest_answer_too_weak_to_follow_up",
        )

    return ConversationDecision(
        action=ConversationAction.FOLLOW_UP,
        stage="DEEPEN",
        reason=(
            "continue_current_topic_without_recall_candidate"
            if candidate_count <= 0
            else "continue_grounded_topic"
        ),
    )


def decide_recall_timing(
    *,
    matured_candidate_count: int,
    latest_is_memory_candidate: bool,
    latest_has_followup_context: bool,
    latest_is_low_info: bool,
    latest_is_negative: bool,
) -> RecallTimingDecision:
    if matured_candidate_count < MIN_MATURED_RECALL_CANDIDATES:
        return RecallTimingDecision(
            action=RecallTimingAction.WAIT,
            reason="not_enough_matured_memories",
        )

    if latest_is_negative:
        return RecallTimingDecision(
            action=RecallTimingAction.WAIT,
            reason="respond_to_negative_emotion_first",
        )

    if (
        matured_candidate_count == MIN_MATURED_RECALL_CANDIDATES
        and latest_has_followup_context
        and not latest_is_memory_candidate
        and not latest_is_low_info
    ):
        return RecallTimingDecision(
            action=RecallTimingAction.WAIT,
            reason="continue_latest_topic_before_recall",
        )

    return RecallTimingDecision(
        action=RecallTimingAction.ASK_RECALL,
        reason="natural_recall_opportunity",
    )
