"""Serialize retries of one DB record within a single AI worker process."""
from contextlib import contextmanager
from dataclasses import dataclass, field
from functools import wraps
from threading import Lock
from typing import Hashable


@dataclass
class _Entry:
    lock: Lock = field(default_factory=Lock)
    users: int = 0


_registry_lock = Lock()
_entries: dict[Hashable, _Entry] = {}


@contextmanager
def record_execution(record_id: Hashable):
    with _registry_lock:
        entry = _entries.setdefault(record_id, _Entry())
        entry.users += 1
    try:
        with entry.lock:
            yield
    finally:
        with _registry_lock:
            entry.users -= 1
            if entry.users == 0:
                del _entries[record_id]


def serialize_record(function):
    @wraps(function)
    def wrapped(record_id, *args, **kwargs):
        # A waiting retry re-reads persisted state after its predecessor ends.
        with record_execution(record_id):
            return function(record_id, *args, **kwargs)
    return wrapped


def serialize_session(function):
    @wraps(function)
    def wrapped(record_id, session_id, *args, **kwargs):
        with record_execution(('session-dialogue', session_id)):
            return function(record_id, session_id, *args, **kwargs)
    return wrapped
