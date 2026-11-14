"""Synchronous stage timings without argument, response, or exception contents."""
import logging
from functools import wraps
from time import perf_counter

logger = logging.getLogger(__name__)


def timed_stage(name):
    def decorate(function):
        @wraps(function)
        def measured(*args, **kwargs):
            started = perf_counter()
            outcome = "returned"
            try:
                return function(*args, **kwargs)
            except BaseException:
                outcome = "raised"
                raise
            finally:
                try:
                    logger.info("dialogue_stage name=%s outcome=%s elapsed_ms=%.1f",
                                name, outcome, (perf_counter() - started) * 1000)
                except Exception:
                    # Diagnostic failures must not replace a reply or its error.
                    pass
        return measured
    return decorate
