from __future__ import annotations

from functools import lru_cache

from kiwipiepy import Kiwi

from recall.memory_event import MemoryAnswerType, parse_answer_type


_kiwi = Kiwi()
_NOUN_TAG_PREFIXES = ("NN",)
_VALUE_TAGS = {"SL", "SH", "SN", "MM"}
_TIME_NOUNS = {"후", "전", "동안", "때"}
_NEGATION_FORMS = {"안", "못", "않"}
_CORRECTION_MARKERS = ("아니,", "아니 ", "정정하면", "다시 말하면")


def is_correction_utterance(text: str) -> bool:
    normalized = " ".join(str(text or "").split())
    return any(marker in normalized for marker in _CORRECTION_MARKERS)
_GENERIC_NOUNS = {
    "것",
    "거",
    "그것",
    "아무것",
    "이것",
    "저것",
    "거기",
    "여기",
    "저기",
    "누구",
    "무엇",
    "뭐",
    "일",
    "때",
    "오늘",
    "아까",
    "이야기",
    "기억",
}


@lru_cache(maxsize=2048)
def _tokens(text: str):
    return tuple(_kiwi.tokenize(" ".join(str(text or "").split())))


def _is_value_token(token) -> bool:
    return token.tag.startswith(_NOUN_TAG_PREFIXES) or token.tag in _VALUE_TAGS


def _span_before_particle(
    text: str,
    tokens,
    particle_index: int,
    *,
    include_conjunctions: bool = True,
) -> str:
    end_index = particle_index - 1

    if end_index < 0 or not _is_value_token(tokens[end_index]):
        return ""

    start_index = end_index

    while start_index > 0:
        previous = tokens[start_index - 1]

        if _is_value_token(previous) or (
            include_conjunctions and previous.tag == "JC"
        ):
            start_index -= 1
            continue

        break

    start = tokens[start_index].start
    end_token = tokens[end_index]
    value = text[start : end_token.start + end_token.len].strip()
    return value if _has_specific_tokens(tokens[start_index:particle_index]) else ""


def _has_specific_tokens(tokens) -> bool:
    nouns = [
        token.form
        for token in tokens
        if token.tag.startswith(_NOUN_TAG_PREFIXES)
        and token.form not in _GENERIC_NOUNS
    ]
    return bool(nouns)


def _particle_candidates(text: str, answer_type: MemoryAnswerType) -> list[str]:
    tokens = _tokens(text)
    allowed = {
        MemoryAnswerType.PERSON: {"와", "과", "랑", "이랑", "에게", "한테"},
        MemoryAnswerType.PLACE: {"에", "에서", "으로", "로"},
        MemoryAnswerType.TIME: {"에", "부터", "까지"},
        MemoryAnswerType.FOOD: {"을", "를"},
        MemoryAnswerType.MEDIA: {"을", "를"},
        MemoryAnswerType.OBJECT: {"을", "를"},
        MemoryAnswerType.ACTIVITY: {"을", "를"},
    }.get(answer_type, set())
    required_tag = {
        MemoryAnswerType.PERSON: "JKB",
        MemoryAnswerType.PLACE: "JKB",
        MemoryAnswerType.TIME: "JKB",
        MemoryAnswerType.FOOD: "JKO",
        MemoryAnswerType.MEDIA: "JKO",
        MemoryAnswerType.OBJECT: "JKO",
        MemoryAnswerType.ACTIVITY: "JKO",
    }.get(answer_type)
    candidates = []

    for index, token in enumerate(tokens):
        if token.form not in allowed or token.tag != required_tag:
            continue

        value = _span_before_particle(text, tokens, index)

        if value:
            candidates.append(value)

    return candidates


def _nominal_action_candidate(text: str) -> str:
    tokens = _tokens(text)

    for index, token in enumerate(tokens[:-1]):
        if not token.tag.startswith(_NOUN_TAG_PREFIXES):
            continue

        next_token = tokens[index + 1]

        if next_token.tag.startswith("XSV"):
            return token.form

    return ""


def _standalone_noun_phrase(text: str, *, max_nouns: int | None = None) -> str:
    tokens = _tokens(text)
    noun_tokens = [token for token in tokens if _is_value_token(token)]

    if not noun_tokens:
        return ""

    if max_nouns is not None and len(noun_tokens) > max_nouns:
        return ""

    start = noun_tokens[0].start
    end_token = noun_tokens[-1]
    value = text[start : end_token.start + end_token.len].strip()
    return value if _has_specific_tokens(noun_tokens) else ""


def extract_answer_value(
    text: str,
    answer_type: MemoryAnswerType | str,
) -> str:
    text = " ".join(str(text or "").split())
    answer_type = parse_answer_type(answer_type)

    if not text or answer_type == MemoryAnswerType.UNKNOWN:
        return ""

    candidates = _particle_candidates(text, answer_type)

    if candidates:
        if answer_type == MemoryAnswerType.TIME:
            timed = [
                value
                for value in candidates
                if any(
                    token.tag in {"NNB", "SN", "MM"}
                    for token in _tokens(value)
                )
            ]
            if timed:
                return timed[0]

        return candidates[-1]

    if answer_type == MemoryAnswerType.ACTIVITY:
        action = _nominal_action_candidate(text)
        return action

    if answer_type == MemoryAnswerType.PERSON:
        return _standalone_noun_phrase(text, max_nouns=1)

    return _standalone_noun_phrase(text)


def extract_memory_clues(
    text: str,
    preferred_type: MemoryAnswerType | str = MemoryAnswerType.UNKNOWN,
) -> tuple[dict[str, str], ...]:
    text = " ".join(str(text or "").split())
    preferred_type = parse_answer_type(preferred_type)

    if not text:
        return ()

    analysis_text = text

    for marker in _CORRECTION_MARKERS:
        if marker in analysis_text:
            corrected = analysis_text.rsplit(marker, 1)[-1].strip(" ,.")
            if corrected:
                analysis_text = corrected
                break

    tokens = _tokens(analysis_text)

    if any(
        token.form in _NEGATION_FORMS
        or token.form.startswith("않")
        for token in tokens
    ):
        return ()

    clues = []

    def add(answer_type: MemoryAnswerType, value: str) -> None:
        value = " ".join(str(value or "").split())
        clue = {
            "answerType": answer_type.value,
            "answerValue": value,
        }

        if value and clue not in clues:
            clues.append(clue)

    for index, token in enumerate(tokens):
        if token.tag == "JKB" and token.form in {"와", "과", "랑", "이랑"}:
            add(
                MemoryAnswerType.PERSON,
                _span_before_particle(
                    analysis_text,
                    tokens,
                    index,
                    include_conjunctions=False,
                ),
            )
            continue

        if (
            token.tag == "JC"
            and token.form in {"와", "과", "랑", "이랑"}
            and preferred_type
            in {
                MemoryAnswerType.ACTIVITY,
                MemoryAnswerType.PLACE,
            }
        ):
            add(
                MemoryAnswerType.PERSON,
                _span_before_particle(
                    analysis_text,
                    tokens,
                    index,
                    include_conjunctions=False,
                ),
            )
            continue

        if token.tag != "JKB" or token.form not in {"에", "에서"}:
            continue

        value = _span_before_particle(
            analysis_text,
            tokens,
            index,
            include_conjunctions=False,
        )

        if not value:
            continue

        value_tokens = tokens[:index]
        start = next(
            (
                token_index
                for token_index, current in enumerate(tokens[:index])
                if current.start >= analysis_text.find(value)
            ),
            0,
        )
        span_tokens = value_tokens[start:]
        is_time = any(
            current.tag in {"NNB", "SN", "MM"}
            or current.form in _TIME_NOUNS
            for current in span_tokens
        )
        add(
            MemoryAnswerType.TIME if is_time else MemoryAnswerType.PLACE,
            value,
        )

    if preferred_type != MemoryAnswerType.UNKNOWN:
        add(
            preferred_type,
            extract_answer_value(analysis_text, preferred_type),
        )

    action = _nominal_action_candidate(analysis_text)
    if action:
        add(MemoryAnswerType.ACTIVITY, action)

    return tuple(clues)
