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
    "으로는",
    "하고는",
    "이랑",
    "랑",
    "에서",
    "에게",
    "으로",
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
    "도",
    "만",
]


KOREAN_VERB_SUFFIXES = [
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


def clean_text(text: str) -> str:
    text = str(text)
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def clamp_score(score: float) -> float:
    return round(max(0.0, min(float(score), 100.0)), 2)


def normalize_keyword_token(token: str) -> str:
    token = clean_text(token)

    for suffix in KOREAN_PARTICLE_SUFFIXES:
        if len(token) > len(suffix) + 1 and token.endswith(suffix):
            token = token[: -len(suffix)]
            break

    for suffix in KOREAN_VERB_SUFFIXES:
        if len(token) > len(suffix) and token.endswith(suffix):
            token = token[: -len(suffix)]
            break

    return token


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

        if len(keyword) < 2 and keyword not in SHORT_ACTION_KEYWORDS:
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

    if keyword in current_text:
        return True

    normalized_keyword = normalize_keyword_token(keyword)
    normalized_current_tokens = [
        normalize_keyword_token(token)
        for token in current_text.split()
    ]

    return normalized_keyword in normalized_current_tokens


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
    current_text = clean_text(current_text)

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
        if is_keyword_matched(keyword, current_text):
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

    similarity_score = calculate_similarity_score(
        past_text=past_text,
        current_text=current_text,
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
