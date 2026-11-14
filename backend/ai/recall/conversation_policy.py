from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class ConversationAction(str, Enum):
    OPEN_TOPIC = "OPEN_TOPIC"
    FOLLOW_UP = "FOLLOW_UP"
    CHANGE_TOPIC = "CHANGE_TOPIC"
    RESUME_AFTER_RECALL = "RESUME_AFTER_RECALL"
    ACKNOWLEDGE = "ACKNOWLEDGE"


class RecallTimingAction(str, Enum):
    WAIT = "WAIT"
    ASK_RECALL = "ASK_RECALL"


MAX_CONSECUTIVE_TOPIC_TURNS = 3
MIN_MATURED_RECALL_CANDIDATES = 1
MAX_MEMORY_WAIT_TURNS = 3
MAX_MEMORY_WAIT_SECONDS = 120


@dataclass(frozen=True)
class ConversationDecision:
    action: ConversationAction
    stage: str
    reason: str
    target_answer_type: str | None = None


@dataclass(frozen=True)
class ConversationReply:
    text: str
    stage: str
    topic: str | None
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
    has_sufficient_detail: bool = False,
    is_unanswered: bool = False,
    repeated_unanswered: bool = False,
    available_answer_types: tuple[str, ...] | None = None,
) -> ConversationDecision:
    if after_recall_answer:
        return ConversationDecision(
            action=ConversationAction.RESUME_AFTER_RECALL,
            stage="DEEPEN",
            reason="recall_answer_completed",
        )

    if repeated_unanswered:
        return ConversationDecision(
            action=ConversationAction.ACKNOWLEDGE,
            stage="OPEN",
            reason="stop_unanswered_question_cycle",
        )

    if should_change_topic:
        return ConversationDecision(
            action=ConversationAction.CHANGE_TOPIC,
            stage="OPEN",
            reason="latest_answer_cannot_support_a_followup",
        )

    if is_unanswered and has_followup_context:
        return ConversationDecision(
            action=ConversationAction.CHANGE_TOPIC,
            stage="OPEN",
            reason="respect_unanswered_question",
        )

    if has_sufficient_detail:
        return ConversationDecision(
            action=ConversationAction.CHANGE_TOPIC,
            stage="OPEN",
            reason="episode_has_grounded_detail",
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

    if has_followup_context and available_answer_types == ():
        return ConversationDecision(
            action=ConversationAction.CHANGE_TOPIC,
            stage="OPEN",
            reason="no_unasked_event_details",
        )

    target = available_answer_types[0] if available_answer_types else None
    if needs_memory_detail:
        return ConversationDecision(
            action=ConversationAction.FOLLOW_UP,
            stage="ANCHOR",
            reason="request_one_grounded_memory_detail",
            target_answer_type=target,
        )

    if not has_followup_context:
        return ConversationDecision(
            action=ConversationAction.OPEN_TOPIC,
            stage="DEEPEN",
            reason="latest_answer_has_no_topic_to_follow_up",
        )

    return ConversationDecision(
        action=ConversationAction.FOLLOW_UP,
        stage="DEEPEN",
        target_answer_type=target,
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
    latest_continues_event: bool = False,
    memory_wait_turns: int = 0,
    memory_wait_seconds: float | None = None,
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

    if memory_wait_seconds is not None and memory_wait_seconds >= MAX_MEMORY_WAIT_SECONDS:
        return RecallTimingDecision(
            action=RecallTimingAction.ASK_RECALL,
            reason="grounded_memory_time_limit_reached",
        )

    if memory_wait_turns >= MAX_MEMORY_WAIT_TURNS:
        return RecallTimingDecision(
            action=RecallTimingAction.ASK_RECALL,
            reason="grounded_memory_wait_limit_reached",
        )

    if (
        matured_candidate_count == MIN_MATURED_RECALL_CANDIDATES
        and latest_has_followup_context
        and not latest_is_memory_candidate
        and not latest_is_low_info
        and not latest_continues_event
    ):
        return RecallTimingDecision(
            action=RecallTimingAction.WAIT,
            reason="continue_latest_topic_before_recall",
        )

    return RecallTimingDecision(
        action=RecallTimingAction.ASK_RECALL,
        reason="natural_recall_opportunity",
    )
