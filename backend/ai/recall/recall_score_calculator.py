import re
from typing import List, Optional, Tuple

from sentence_transformers import SentenceTransformer, util


embedding_model = SentenceTransformer("jhgan/ko-sroberta-multitask")


KEYWORD_STOPWORDS = {
    "오늘",
    "어제",
    "그냥",
    "정도",
    "있어요",
    "했어요",
    "합니다",
    "그리고",
    "그래서",
    "저는",
    "제가",
}


KOREAN_PARTICLE_SUFFIXES = [
    "에서는",
    "에게는",
    "한테는",
    "께서는",
    "으로는",
    "하고는",
    "이랑",
    "랑",
    "에서",
    "에게",
    "한테",
    "께서",
    "까지",
    "부터",
    "으로",
    "하고",
    "이나",
    "로",
    "은",
    "는",
    "이",
    "가",
    "을",
    "를",
    "에",
    "와",
    "과",
    "께",
    "도",
    "만",
]


KOREAN_VERB_SUFFIXES = [
    "이었어요",
    "이었죠",
    "이었지",
    "이었어",
    "였어요",
    "였죠",
    "였지",
    "였어",
    "이죠",
    "이지",
    "이야",
    "이라고요",
    "이에요",
    "이라고",
    "라고요",
    "입니다",
    "예요",
    "라고",
    "었습니다",
    "았습니다",
    "습니다",
    "했어요",
    "했어",
    "었어요",
    "았어요",
    "었어",
    "았어",
    "었다",
    "았다",
    "어요",
    "아요",
    "해요",
    "음",
    "요",
]


SHORT_ACTION_KEYWORDS = {
    "먹",
    "마시",
    "가",
    "갔",
    "오",
    "왔",
    "봄",
    "봐",
    "했",
}


SHORT_KEYWORD_WORDS = {
    "딸",
    "약",
    "국",
    "배",
    "집",
    "밥",
    "물",
    "차",
    "책",
    "꽃",
    "옷",
    "산",
    "눈",
    "손",
    "발",
    "팔",
    "방",
    "길",
    "비",
}


NON_PARTICLE_COMPOUND_WORDS = {
    "약과",
    "국가",
}


def clean_text(text: str) -> str:
    text = str(text)
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def clamp_score(score: float) -> float:
    return round(max(0.0, min(float(score), 100.0)), 2)


def normalize_keyword_token(token: str) -> str:
    token = clean_text(token)

    for suffix in KOREAN_VERB_SUFFIXES:
        if len(token) > len(suffix) and token.endswith(suffix):
            token = token[: -len(suffix)]
            break

    if len(token) > 2 and token.endswith("야"):
        token = token[:-1]

    if token in NON_PARTICLE_COMPOUND_WORDS:
        return token

    for suffix in KOREAN_PARTICLE_SUFFIXES:
        candidate = token[:-len(suffix)] if token.endswith(suffix) else ""

        if (
            candidate
            and (len(candidate) >= 2 or candidate in SHORT_KEYWORD_WORDS)
        ):
            token = candidate
            break

    return token


def is_single_syllable_keyword_matched(keyword: str, current_text: str) -> bool:
    allowed_endings = set(KOREAN_PARTICLE_SUFFIXES) | {
        "이야",
        "야",
        "이에요",
        "예요",
        "이었어",
        "이었어요",
        "였어",
        "였어요",
        "입니다",
        "이요",
        "요",
        "이라고",
        "이라고요",
    }
    allowed_endings.update(f"{particle}요" for particle in KOREAN_PARTICLE_SUFFIXES)

    for token in clean_text(current_text).split():
        if token == keyword:
            return True

        if keyword not in SHORT_KEYWORD_WORDS:
            continue

        if token in NON_PARTICLE_COMPOUND_WORDS or not token.startswith(keyword):
            continue

        if token[len(keyword):] in allowed_endings:
            return True

    return False


def extract_keywords_from_text(
    text: str,
    max_keywords: int = 5,
) -> List[str]:
    text = clean_text(text)

    if not text:
        return []

    keywords = []
    seen = set()

    for token in text.split():
        keyword = normalize_keyword_token(token)

        if (
            len(keyword) < 2
            and keyword not in SHORT_ACTION_KEYWORDS
            and keyword not in SHORT_KEYWORD_WORDS
        ):
            continue

        if keyword in KEYWORD_STOPWORDS:
            continue

        if keyword in seen:
            continue

        seen.add(keyword)
        keywords.append(keyword)

        if len(keywords) >= max_keywords:
            break

    return keywords


def is_keyword_matched(
    keyword: str,
    current_text: str,
) -> bool:
    keyword = clean_text(keyword)
    current_text = clean_text(current_text)

    if not keyword or not current_text:
        return False

    if len(keyword) == 1:
        return is_single_syllable_keyword_matched(keyword, current_text)

    normalized_keyword_tokens = [
        normalize_keyword_token(token)
        for token in keyword.split()
        if normalize_keyword_token(token)
    ]
    normalized_current_tokens = [
        normalize_keyword_token(token)
        for token in current_text.split()
        if normalize_keyword_token(token)
    ]

    if not normalized_keyword_tokens or not normalized_current_tokens:
        return False

    target = "".join(normalized_keyword_tokens)

    for start_index in range(len(normalized_current_tokens)):
        combined = ""

        for end_index in range(start_index, len(normalized_current_tokens)):
            combined += normalized_current_tokens[end_index]

            if combined == target:
                return True

            if len(combined) >= len(target):
                break

    return False


def is_uncertain_answer(current_text: str) -> bool:
    current_text = clean_text(current_text)

    if any(
        phrase in current_text
        for phrase in (
            "기억이 안",
            "기억 안",
            "생각이 안",
            "생각 안",
            "모르겠",
            "몰라",
            "잘 모르",
            "아마",
            "것 같",
            "같아",
            "같아요",
            "확실하지 않",
            "확실하진 않",
            "일지도",
            "일 수도",
            "일수도",
        )
    ):
        return True

    return False


def is_keyword_uncertain(keyword: str, current_text: str) -> bool:
    clauses = re.split(
        r"[,.;!?]|(?:지만|는데)\s+",
        str(current_text or ""),
    )
    keyword_clauses = [
        clause
        for clause in clauses
        if is_keyword_matched(keyword, clause)
    ]

    if not keyword_clauses:
        return False

    return all(is_uncertain_answer(clause) for clause in keyword_clauses)


def get_final_correction_segment(current_text: str) -> str:
    text = str(current_text or "")
    correction_patterns = (
        r"다시\s*생각해\s*보니",
        r"정정(?:할게요?|하면)",
        r"(?:^|[,.;!?]\s*|\s+)(?:아니에요|아니요|아니)(?![가-힣])\s*[,，]?\s*",
    )
    last_end = -1

    for pattern in correction_patterns:
        for match in re.finditer(pattern, text):
            last_end = max(last_end, match.end())

    corrected_text = text if last_end < 0 else text[last_end:].strip()
    alternative_matches = list(
        re.finditer(r"(?:아니라|아니고|말고)\s*", corrected_text)
    )

    if alternative_matches:
        alternative_text = corrected_text[alternative_matches[-1].end():].strip()

        if alternative_text:
            corrected_text = alternative_text

    return corrected_text or text


def is_keyword_explicitly_rejected(keyword: str, current_text: str) -> bool:
    compact_keyword = re.sub(r"\s+", "", clean_text(keyword))

    if not compact_keyword:
        return False

    rejection_markers = (
        "아니라",
        "아니고",
        "아니야",
        "아니에요",
        "아닙니다",
        "아닌것같",
        "말고",
    )

    clauses = re.split(
        r"[,.;!?]|\s+(?:그런데|하지만|그리고)\s+",
        str(current_text or ""),
    )

    for clause in clauses:
        if not is_keyword_matched(keyword, clause):
            continue

        compact_clause = re.sub(r"\s+", "", clean_text(clause))
        keyword_index = compact_clause.find(compact_keyword)

        if keyword_index < 0:
            continue

        for marker in rejection_markers:
            marker_index = compact_clause.find(marker)

            if marker_index >= keyword_index + len(compact_keyword):
                between_keyword_and_marker = compact_clause[
                    keyword_index + len(compact_keyword):marker_index
                ]
                connective_text = between_keyword_and_marker.replace(
                    "이라고",
                    "",
                ).replace("라고", "")
                connective_index = connective_text.find("고")

                if (
                    connective_index >= 0
                    and len(connective_text[connective_index + 1:]) >= 2
                ):
                    continue

                return True

        negated_action_patterns = (
            r"(?:^|\s)(?:안|못)\s*"
            r"(?:먹|마시|가|다녀|보|만나|사|하|오|나가|듣|쉬|자|읽|쓰|타|통화|전화|연락)[가-힣]*",
            r"(?:^|\s)[가-힣]+지\s*않[가-힣]*",
            r"(?:^|\s)[가-힣]+지\s*못[가-힣]*",
            r"(?:^|\s)[가-힣]+(?:어|아)?본?\s*적(?:이)?\s*없[가-힣]*",
            r"(?:^|\s)[가-힣]+(?:은|ㄴ)\s*적(?:이)?\s*없[가-힣]*",
        )

        for pattern in negated_action_patterns:
            for match in re.finditer(pattern, clean_text(clause)):
                compact_before_match = re.sub(
                    r"\s+",
                    "",
                    clean_text(clause)[:match.start()],
                )

                if len(compact_before_match) >= keyword_index + len(compact_keyword):
                    return True

    return False


def get_recall_weights(question_type: str) -> Tuple[float, float]:
    question_type = str(question_type or "DEFAULT").upper()

    weights = {
        "FACT": (0.2, 0.8),
        "PREFERENCE": (0.4, 0.6),
        "MEMORY": (0.6, 0.4),
        "DAILY": (0.3, 0.7),
        "DEFAULT": (0.4, 0.6),
        "INITIAL": (0.4, 0.6),
        "RECALL": (0.4, 0.6),
        "CONVERSATION": (0.4, 0.6),
    }

    return weights.get(question_type, (0.4, 0.6))


def calculate_similarity_score(
    past_text: str,
    current_text: str,
) -> float:
    past_text = clean_text(past_text)
    current_text = clean_text(current_text)

    if not past_text or not current_text:
        return 0.0

    past_embedding = embedding_model.encode(
        past_text,
        convert_to_tensor=True,
    )
    current_embedding = embedding_model.encode(
        current_text,
        convert_to_tensor=True,
    )

    score = util.cos_sim(
        past_embedding,
        current_embedding,
    ).item()

    return clamp_score(score * 100)


def calculate_keyword_score(
    keywords: Optional[List[str]],
    current_text: str,
) -> float:
    raw_current_text = str(current_text or "")
    scoring_text = get_final_correction_segment(raw_current_text)
    current_text = clean_text(scoring_text)

    if not keywords:
        return 0.0

    cleaned_keywords = []

    for keyword in keywords:
        cleaned_keyword = clean_text(keyword)

        if cleaned_keyword:
            cleaned_keywords.append(cleaned_keyword)

    if not cleaned_keywords:
        return 0.0

    matched = 0

    for keyword in cleaned_keywords:
        if (
            is_keyword_matched(keyword, current_text)
            and not is_keyword_uncertain(keyword, scoring_text)
            and not is_keyword_explicitly_rejected(keyword, scoring_text)
        ):
            matched += 1

    return clamp_score((matched / len(cleaned_keywords)) * 100)


def calculate_final_recall_score(
    past_text: str,
    current_text: str,
    keywords: Optional[List[str]] = None,
    question_type: str = "DEFAULT",
) -> dict:
    if not keywords:
        keywords = extract_keywords_from_text(past_text)

    scoring_current_text = get_final_correction_segment(current_text)
    similarity_score = calculate_similarity_score(
        past_text=past_text,
        current_text=scoring_current_text,
    )

    keyword_score = calculate_keyword_score(
        keywords=keywords,
        current_text=current_text,
    )

    similarity_weight, keyword_weight = get_recall_weights(question_type)

    final_recall_score = (
        similarity_score * similarity_weight
        + keyword_score * keyword_weight
    )

    normalized_question_type = str(question_type or "DEFAULT").upper()

    if normalized_question_type == "FACT" and keyword_score == 100:
        final_recall_score = max(final_recall_score, 90)

    if normalized_question_type == "FACT" and keyword_score == 0:
        final_recall_score = min(final_recall_score, 49)

    final_recall_score = clamp_score(final_recall_score)

    return {
        "similarityScore": clamp_score(similarity_score),
        "keywordScore": clamp_score(keyword_score),
        "finalRecallScore": final_recall_score,
        "usedKeywords": keywords or [],

        # 민정님 커밋 기준:
        # textScore는 회상 파트의 의미 유사도 점수(similarityScore)로 저장한다.
        "textScore": clamp_score(similarity_score),

        # recallScore는 키워드까지 반영한 최종 회상 점수다.
        "recallScore": final_recall_score,
    }


def calculate_text_score(
    past_text: str,
    current_text: str,
    keywords: Optional[List[str]] = None,
    question_type: str = "DEFAULT",
) -> dict:
    return calculate_final_recall_score(
        past_text=past_text,
        current_text=current_text,
        keywords=keywords,
        question_type=question_type,
    )


def convert_recall_score_to_risk_score(
    recall_score: float,
) -> float:
    return clamp_score(100.0 - clamp_score(recall_score))


def calculate_final_risk_score(
    speech_risk_score: float,
    recall_score: float,
) -> dict:
    speech_risk_score = clamp_score(speech_risk_score)
    recall_score = clamp_score(recall_score)
    recall_risk_score = convert_recall_score_to_risk_score(recall_score)

    final_risk_score = (
        speech_risk_score * 0.3
        + recall_risk_score * 0.7
    )

    final_risk_score = clamp_score(final_risk_score)

    if final_risk_score < 30:
        risk_level = "LOW"
    elif final_risk_score < 60:
        risk_level = "MEDIUM"
    else:
        risk_level = "HIGH"

    return {
        "speechScore": speech_risk_score,
        "recallRiskScore": recall_risk_score,
        "finalRiskScore": final_risk_score,
        "riskLevel": risk_level,
    }


if __name__ == "__main__":
    past = "오늘 점심에는 김치찌개를 먹었어요."
    current = "김치찌개 먹었어요."
    keywords = ["김치찌개"]

    recall_result = calculate_text_score(
        past_text=past,
        current_text=current,
        keywords=keywords,
        question_type="RECALL",
    )

    print("회상 분석 결과")
    print(recall_result)

    risk_result = calculate_final_risk_score(
        speech_risk_score=25.0,
        recall_score=recall_result["recallScore"],
    )

    print("최종 위험도 결과")
    print(risk_result)
