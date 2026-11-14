"""Opt-in server factory; prepare local dialogue models before accepting speech."""
import logging
from time import perf_counter


def warmup_dialogue_models():
    import voice_reply_handler as handler
    from recall import conversation_recall_generator as generator
    from recall.memory_evidence_extractor import extract_predicate_forms

    # Synthetic text only. No gateway, Spring, scoring or persistence calls.
    sample = "오늘 집에서 책을 읽었어요."
    started = perf_counter()
    handler._tokenize_cached(sample)
    generator._tokenize_cached(sample)
    extract_predicate_forms(sample)
    elapsed_ms = (perf_counter() - started) * 1000
    logging.getLogger(__name__).info("dialogue_warmup_ms=%.1f", elapsed_ms)
    return elapsed_ms


def create_app():
    from fastapi_ai import app
    warmup_dialogue_models()
    return app
