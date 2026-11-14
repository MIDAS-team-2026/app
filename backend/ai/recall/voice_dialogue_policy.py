"""Voice-turn goals and evidence checks, independent of topic-specific wording."""
from collections import Counter
import re

from recall.episode_context import active_episode_records, ordered_records
from recall.event_modality import EventMode, classify_event
from recall.conversation_recall_generator import _tokenize_cached, _get_final_correction_segment, score_recall_memory_candidate, extract_memory_action_concepts
from recall.memory_evidence_extractor import is_context_only_place, extract_content_nouns, extract_memory_clues, extract_predicate_forms
from recall.followup_intent import infer_followup_target
from recall.uncertain_reply import declines_detail
from recall.dialogue_act import conversation_control


def question_part(reply: str) -> str:
    return next((part.strip() for part in reversed(re.split(r"[.!。！]", reply))
                 if "?" in part or "？" in part), reply.strip())


def asks_about_habit(question: str) -> bool:
    return any(token.form in {"요즘", "주로", "보통", "자주", "평소", "매일"}
               for token in _tokenize_cached(question))


def asks_about_event(question: str) -> bool:
    tokens = _tokenize_cached(question)
    past = any(token.tag == "EP" and token.form in {"었", "았", "였"} for token in tokens)
    completed_modifier = any(
        token.tag == "ETM" and token.form in {"ㄴ", "ᆫ", "은", "던"}
        and any(previous.tag.startswith("VV") or previous.tag == "XSV"
                for previous in tokens[max(0, index - 3):index])
        for index, token in enumerate(tokens)
    )
    return (past or completed_modifier) and not asks_about_habit(question)


def _affirmative_texts(texts):
    corrected = [_get_final_correction_segment(text) for text in texts]
    return [text for text in corrected if not any(
        token.form in {"안", "못", "않", "아니", "없"} for token in _tokenize_cached(text)
    )]


def _detail_tokens(text, *, person=False):
    tokens = _tokenize_cached(text)
    return tuple(token.form for index, token in enumerate(tokens) if not (
        person and index and token.form in {"분", "님"} and token.tag in {"NNB", "XSN"}
        and tokens[index - 1].tag.startswith("NN")
        and tokens[index - 1].start + tokens[index - 1].len == token.start
    ))


def _time_scopes(text):
    forms = {token.form for token in _tokenize_cached(text)}
    return [sorted(forms.intersection(group)) for group in (
        {"오늘", "어제", "그제"}, {"아침", "점심", "저녁"}, {"오전", "오후"})]


def _different_time_scope(question, context):
    return any(known and requested and not set(known).intersection(requested)
               for known, requested in zip(context.get("active_time_scopes", []), _time_scopes(question)))


def _subject_terms(text):
    terms = set(extract_content_nouns(text))
    run = []
    for token in _tokenize_cached(text):
        if not token.tag.startswith("NN"):
            run = []
            continue
        if run and run[-1].start + run[-1].len != token.start:
            run = []
        run.append(token)
        # A compound can tokenize differently in an answer and a question.
        if len(run) > 1 and any(part.form in terms for part in run):
            terms.add(text[run[0].start:token.start + token.len])
    return terms


def _broad_activity_question(question, context):
    if infer_followup_target(question) not in {"UNKNOWN", "ACTIVITY"}:
        return False
    if _subject_terms(question).intersection(context.get("active_subject_terms", [])):
        return False
    forms = {token.form for token in _tokenize_cached(question)}
    if not forms.intersection({"무엇", "뭐", "어떤", "어떻", "어떻게", "일", "순간"}):
        return False
    # Generic support/request verbs do not name an activity. Concrete predicates
    # such as sewing/reading remain open-ended; no activity catalogue is needed.
    return not (extract_predicate_forms(question) - {
        "하", "있", "없", "지내", "보내", "말씀하", "말하", "주",
        "떠오르", "생각나", "기억나", "되",
    })


def add_voice_policy(context: dict, records: list[dict]) -> None:
    ordered = ordered_records(records)
    recent = ordered[-12:]
    active = active_episode_records(records)
    mode = classify_event(str(recent[-1].get("transcriptText") or ""))
    experienced = any(
        classify_event(str(row.get("transcriptText") or "")) == EventMode.EXPERIENCED
        and score_recall_memory_candidate(str(row.get("transcriptText") or ""))["isValid"]
        for row in active
    )
    context["dialogue_goal"] = (
        "RESPECT_INTENT" if mode in {EventMode.WISH, EventMode.PLAN}
        else "DEVELOP_EVENT" if experienced else "ELICIT_EVENT"
    )
    active_text = " ".join(_affirmative_texts([
        str(row.get("transcriptText") or "") for row in active]))
    active_clues = tuple(clue for clue in extract_memory_clues(active_text)
                        if not (clue["answerType"] == "PLACE"
                                and is_context_only_place(clue["answerValue"])))
    context["known_detail_types"] = sorted({clue["answerType"] for clue in active_clues})
    context["active_time_scopes"] = _time_scopes(active_text)
    places = set().union(*(extract_content_nouns(clue["answerValue"])
                          for clue in active_clues if clue["answerType"] == "PLACE"))
    context["active_subject_terms"] = sorted(_subject_terms(active_text) - places)
    context["active_action_concepts"] = sorted(extract_memory_action_concepts(active_text) - {"ACTIVITY", "GIVE"})
    latest_text = str(recent[-1].get("transcriptText") or "")
    context["unanswered_action_concepts"] = sorted(
        extract_memory_action_concepts(question_part(str(recent[-2].get("aiReplyText") or "")))
        - {"ACTIVITY", "GIVE"}
    ) if len(recent) > 1 and (mode in {EventMode.UNCERTAIN, EventMode.NEGATED} or declines_detail(latest_text)) else []
    declined_actions = set()
    for index, row in enumerate(ordered):
        text = str(row.get("transcriptText") or "")
        if row.get("answerRole") == "FIXED":
            continue
        if index and (conversation_control(text) == "SKIP" or declines_detail(text)
                      or classify_event(text) in {EventMode.UNCERTAIN, EventMode.NEGATED}):
            previous = ordered[index - 1]
            if previous.get("presentedRecallQuestionId") is None and previous.get("conversationStage") != "RECALL_CLARIFY":
                declined_actions.update(extract_memory_action_concepts(
                    question_part(str(previous.get("aiReplyText") or ""))))
        elif not row.get("answerRole") and classify_event(text) == EventMode.EXPERIENCED:
            # A volunteered event can reopen a topic; model-generated questions cannot.
            declined_actions.difference_update(extract_memory_action_concepts(text))
    context["declined_action_concepts"] = sorted(declined_actions - {"ACTIVITY", "GIVE"})
    # The ledger crosses OPEN/topic-ID boundaries: a model cannot reset the
    # questioning budget merely by labelling an old subject as CHANGE_TOPIC.
    attempts = Counter()
    for row in recent[:-1]:
        if row.get("answerRole") == "RECALL" or row.get("conversationStage") == "PAUSED":
            attempts.clear()
            continue
        if row.get("answerRole") or row.get("conversationStage") in {"RECALL_CLARIFY", "PAUSED"}:
            continue
        reply = str(row.get("aiReplyText") or "")
        if "?" in reply or "？" in reply:
            attempts.update(extract_content_nouns(question_part(reply)))
    if experienced and len(active) == 1:
        attempts.clear()
    context["exhausted_subject_terms"] = sorted(term for term, count in attempts.items() if count >= 2)
    context["detail_evidence_record_ids"] = [row["recordId"] for row in active]
    asserted = _affirmative_texts([str(row.get("transcriptText") or "")[:2000]
                                 for row in active])
    context["user_detail_evidence"] = [
        clue for text in asserted for clue in extract_memory_clues(text)
        if clue["answerType"] in {"TIME", "PLACE", "PERSON"}
    ]


def validate_voice_policy(reply: str, action: str, context: dict) -> None:
    question = question_part(reply)
    goal = context.get("dialogue_goal")
    if goal != "RESPECT_INTENT" and asks_about_habit(question):
        raise ValueError("habit_question_without_event")
    if action != "CLARIFY" and (goal == "ELICIT_EVENT" or action == "CHANGE_TOPIC") and not asks_about_event(question):
        if goal != "RESPECT_INTENT":
            raise ValueError("recent_event_question_required")
    subjects = extract_content_nouns(question)
    if subjects.intersection(context.get("exhausted_subject_terms", [])):
        raise ValueError("exhausted_subject_reopened")
    if action != "CLARIFY" and goal != "RESPECT_INTENT" and _broad_activity_question(question, context):
        raise ValueError("broad_activity_question")
    if extract_memory_action_concepts(question).intersection(context.get("unanswered_action_concepts", [])):
        raise ValueError("unanswered_topic_reasked")
    if action == "CHANGE_TOPIC" and extract_memory_action_concepts(question).intersection(
            context.get("declined_action_concepts", [])):
        raise ValueError("declined_topic_reopened")
    if not _different_time_scope(question, context):
        target = infer_followup_target(question)
        if action in {"FOLLOW_UP", "CLARIFY"} and target in {"PLACE", "PERSON"}:
            if target in context.get("known_detail_types", []):
                raise ValueError("known_detail_reasked")
        if action == "CHANGE_TOPIC" and goal == "DEVELOP_EVENT":
            actions = extract_memory_action_concepts(question) - {"ACTIVITY", "GIVE"}
            if (actions.intersection(context.get("active_action_concepts", []))
                    or subjects.intersection(context.get("active_subject_terms", []))):
                raise ValueError("active_topic_reopened")
    if action not in {"FOLLOW_UP", "CLARIFY"}:
        return
    evidence_ids = set(context.get("detail_evidence_record_ids", []))
    known_texts = _affirmative_texts([row["user"] for row in context.get("history", [])
                                    if row["record_id"] in evidence_ids])
    for clue in extract_memory_clues(question):
        if clue["answerType"] not in {"TIME", "PLACE", "PERSON"}:
            continue
        value = re.sub(r"\s+", "", clue["answerValue"])
        # Relative days set the search window of an event question. Named
        # times/places/people must come from the user, not an earlier AI guess.
        person = clue["answerType"] == "PERSON"
        tokens = _detail_tokens(clue["answerValue"], person=person)
        if value in {"오늘", "어제", "최근"} or any(
            token in {"몇", "언제", "어디", "누구", "무엇", "어떤", "어느"} for token in tokens
        ):
            continue
        known_tokens = [_detail_tokens(text, person=person) for text in known_texts]
        if not any(tokens == source[index:index + len(tokens)]
                   for source in known_tokens for index in range(len(source))):
            raise ValueError("unsupported_question_detail")
