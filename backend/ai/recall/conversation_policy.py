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


MAX_CONSECUTIVE_TOPIC_TURNS = 3


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
    consecutive_topic_turns: int = 0,
) -> ConversationDecision:
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

    if (
        has_followup_context
        and consecutive_topic_turns >= MAX_CONSECUTIVE_TOPIC_TURNS
    ):
        return ConversationDecision(
            action=ConversationAction.CHANGE_TOPIC,
            stage="OPEN",
            reason="current_topic_has_enough_detail",
        )

    if candidate_count <= 0 and not has_followup_context:
        return ConversationDecision(
            action=ConversationAction.OPEN_TOPIC,
            stage="DEEPEN",
            reason="no_recall_candidate_in_current_cycle",
        )

    return ConversationDecision(
        action=ConversationAction.FOLLOW_UP,
        stage="ANCHOR" if needs_memory_detail else "DEEPEN",
        reason=(
            "request_one_grounded_memory_detail"
            if needs_memory_detail
            else (
                "continue_current_topic_without_recall_candidate"
                if candidate_count <= 0
                else "continue_grounded_topic"
            )
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
    if matured_candidate_count < 2:
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
        matured_candidate_count == 2
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
