from dataclasses import dataclass
from enum import Enum
from functools import lru_cache

from recall.conversation_recall_generator import (
    _tokenize_cached, extract_memory_action_concepts, score_recall_memory_candidate,
)
from recall.episode_context import episode_segments
from recall.dialogue_act import is_acknowledgement
from recall.followup_intent import infer_followup_target


class EventMode(str, Enum):
    UNKNOWN = "UNKNOWN"
    EXPERIENCED = "EXPERIENCED"
    WISH = "WISH"
    PLAN = "PLAN"
    STATE = "STATE"
    NEGATED = "NEGATED"
    UNCERTAIN = "UNCERTAIN"


NON_EXPERIENCED = frozenset({EventMode.WISH, EventMode.PLAN, EventMode.NEGATED, EventMode.UNCERTAIN})


@dataclass(frozen=True)
class EventFrame:
    mode: EventMode = EventMode.UNKNOWN
    source_text: str = ""

    @property
    def allows_past_evidence(self) -> bool:
        return self.mode not in NON_EXPERIENCED and not (
            self.mode == EventMode.STATE and is_current_state(self.source_text)
        )


@lru_cache(maxsize=256)
def is_current_state(text: str) -> bool:
    tokens = _tokenize_cached(text)
    if any(token.tag == "EP" and token.form in {"었", "았", "였", "더"} for token in tokens):
        return False
    ongoing = any(
        left.form == "고" and left.tag == "EC" and right.form == "있" and right.tag.startswith("V")
        for left, right in zip(tokens, tokens[1:])
    )
    explicitly_now = any(token.form in {"지금", "현재"} for token in tokens)
    habitual = any(token.form in {"평소", "요즘", "주로", "보통", "자주", "매일"} for token in tokens)
    # An action concept alone is not a completed event when the speaker is
    # describing a present routine. Explicit past morphology wins above.
    return ongoing or ((explicitly_now or habitual) and any(token.tag.startswith("V") for token in tokens))


@lru_cache(maxsize=256)
def classify_event(text: str) -> EventMode:
    reasons = set(score_recall_memory_candidate(text)["reasons"])
    if "uncertain_memory" in reasons:
        return EventMode.UNCERTAIN
    if "incomplete_or_negated_action" in reasons:
        return EventMode.NEGATED
    if any(token.form == "싶" and token.tag.startswith("V") for token in _tokenize_cached(text)):
        return EventMode.WISH
    if "future_or_wish_expression" in reasons:
        return EventMode.PLAN
    if is_current_state(text):
        return EventMode.STATE
    if "state_without_action" in reasons:
        return EventMode.STATE
    if extract_memory_action_concepts(text) or "has_general_past_action" in reasons:
        return EventMode.EXPERIENCED
    return EventMode.UNKNOWN


def resolve_event_frames(records: list[dict]) -> dict[int, EventFrame]:
    """Reconstruct modality from the same episode boundaries used for recall.

    A typed detail or reason fragment inherits its event, but a new action or
    explicit denial/uncertainty does not silently become an experienced event.
    """
    frames = {}
    for segment in episode_segments(records):
        frame = EventFrame()
        previous = {}
        actions = set()
        for row in segment:
            text = str(row.get("transcriptText") or "")
            mode = classify_event(text)
            current_actions = extract_memory_action_concepts(text)
            detail = infer_followup_target(str(previous.get("aiReplyText") or "")) != "UNKNOWN"
            fragment = not any(token.tag.startswith("V") for token in _tokenize_cached(text))
            inherits = (
                (detail or is_acknowledgement(text)) and frame.mode != EventMode.UNKNOWN
                and not is_current_state(text)
                and mode not in {EventMode.NEGATED, EventMode.UNCERTAIN, EventMode.WISH}
                and (fragment or not current_actions or (mode in {EventMode.UNKNOWN, EventMode.STATE} and current_actions.issubset(actions)))
            )
            if not inherits:
                frame = EventFrame(mode, text)
                actions = current_actions
            frames[int(row.get("recordId") or 0)] = frame
            previous = row
    return frames
