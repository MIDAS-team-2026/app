"""Conservative local continuation when a voice model proposal is unusable."""
from recall.conversation_recall_generator import extract_memory_action_concepts
from recall.episode_context import active_episode_records, ordered_records
from recall.followup_intent import build_followup_intent_state, infer_followup_target, render_event_followups
from recall.memory_event import MemoryAnswerType
from recall.event_modality import EventMode, classify_event
from recall.uncertain_reply import declines_detail
from recall.conversation_policy import MAX_CONSECUTIVE_TOPIC_TURNS
from recall.voice_dialogue_policy import asks_about_event, question_part
from recall.memory_evidence_extractor import extract_predicate_forms


# Reuse the engine's semantic action categories, not topic-specific sentences.
_TOPIC_ACTIONS = {
    "FOOD": {"EAT"},
    "MEDIA": {"SEE", "HEAR"},
    "PERSON": {"MEET", "CONTACT"},
}


def fallback_candidates(records, legacy_text, legacy_stage, topic_openers):
    """Yield only unasked detail slots or untouched, explicit topic openers.

    Topic history includes denials and unanswered questions: absence of a useful
    answer must not make the system ask the same topic again in other words.
    """
    active = active_episode_records(records)
    intent = build_followup_intent_state(records, [], MemoryAnswerType.UNKNOWN, {})
    target = infer_followup_target(legacy_text)
    if (active and legacy_stage == "DEEPEN" and target != "UNKNOWN"
            and intent.allows(legacy_text)):
        yield legacy_text, "FOLLOW_UP", target

    # If the proposed detail is unusable, try other unasked slots before leaving
    # a grounded event. Preserve the planner's topic-change and burden limits.
    if (active and legacy_stage == "DEEPEN" and len(active) < MAX_CONSECUTIVE_TOPIC_TURNS
            and not intent.has_sufficient_detail
            and not declines_detail(str(active[-1].get("transcriptText") or ""))
            and any(classify_event(str(row.get("transcriptText") or "")) == EventMode.EXPERIENCED
                    for row in active)):
        missing = tuple(slot for slot in ("PERSON", "PLACE", "TIME")
                        if slot not in intent.known_types and slot not in intent.attempted_types)
        for question in render_event_followups(missing, []):
            if question != legacy_text:
                yield question, "FOLLOW_UP", infer_followup_target(question)

    actions = set()
    asked = set()
    predicates = set()
    for row in ordered_records(records):
        if row.get("answerRole") == "FIXED":
            continue
        actions.update(extract_memory_action_concepts(str(row.get("transcriptText") or "")))
        question = str(row.get("aiReplyText") or "")
        predicates.update(extract_predicate_forms(str(row.get("transcriptText") or "")))
        if "?" in question or "？" in question:
            actions.update(extract_memory_action_concepts(question))
            predicates.update(extract_predicate_forms(question))
            asked.add(question_part(question))
    for text, target in topic_openers:
        if text in asked or not asks_about_event(text):
            continue
        concepts = extract_memory_action_concepts(text) or _TOPIC_ACTIONS.get(target, set())
        if actions.intersection(concepts):
            continue
        concrete = extract_predicate_forms(text) - {'하', '있', '없', '되', '주', '보'}
        if concrete and concrete.intersection(predicates):
            continue
        yield text, "CHANGE_TOPIC", target
