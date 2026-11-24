from recall.memory_event import MemoryAnswerType, infer_answer_type
from recall.memory_evidence_extractor import extract_memory_clues
from recall.conversation_recall_generator import extract_memory_action_concepts


def answers_event_detail(text: str, question: str, event_texts: list[str]) -> bool:
    slot = infer_answer_type(question)
    if slot not in {MemoryAnswerType.TIME, MemoryAnswerType.PLACE, MemoryAnswerType.PERSON}:
        return False
    if not any(clue["answerType"] == slot.value for clue in extract_memory_clues(text, slot)):
        return False
    earlier_actions = set().union(*(extract_memory_action_concepts(item) for item in event_texts))
    if not earlier_actions:
        return False
    current_actions = extract_memory_action_concepts(text)
    # A place/time/companion answer with no new action fills the existing event.
    return not current_actions or current_actions.issubset(earlier_actions)
