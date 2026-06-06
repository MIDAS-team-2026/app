import re
from typing import List, Tuple

from sentence_transformers import SentenceTransformer, util


embedding_model = SentenceTransformer("jhgan/ko-sroberta-multitask")


def clean_text(text: str) -> str:
    text = str(text)
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", "", text)
    return text.strip()


def get_recall_weights(question_type: str) -> Tuple[float, float]:
    weights = {
        "FACT": (0.2, 0.8),
        "PREFERENCE": (0.4, 0.6),
        "MEMORY": (0.6, 0.4),
        "DAILY": (0.3, 0.7),
        "DEFAULT": (0.4, 0.6),
        "INITIAL": (0.4, 0.6),
        "RECALL": (0.4, 0.6),
    }
    return weights.get(question_type, (0.4, 0.6))


def calculate_similarity_score(past_text: str, current_text: str) -> float:
    past_text = clean_text(past_text)
    current_text = clean_text(current_text)

    if not past_text or not current_text:
        return 0.0

    past_embedding = embedding_model.encode(past_text, convert_to_tensor=True)
    current_embedding = embedding_model.encode(current_text, convert_to_tensor=True)

    score = util.cos_sim(past_embedding, current_embedding).item()
    return round(max(0.0, score * 100), 2)


def calculate_keyword_score(keywords: List[str], current_text: str) -> float:
    current_text = clean_text(current_text)

    if not keywords:
        return 0.0

    matched = 0

    for keyword in keywords:
        keyword = clean_text(keyword)
        if keyword and keyword in current_text:
            matched += 1

    return round((matched / len(keywords)) * 100, 2)


def calculate_final_recall_score(
    past_text: str,
    current_text: str,
    keywords: List[str],
    question_type: str = "DEFAULT",
) -> dict:
    similarity_score = calculate_similarity_score(past_text, current_text)
    keyword_score = calculate_keyword_score(keywords, current_text)

    similarity_weight, keyword_weight = get_recall_weights(question_type)

    final_recall_score = (
        similarity_score * similarity_weight
        + keyword_score * keyword_weight
    )

    if question_type == "FACT" and keyword_score == 100:
        final_recall_score = max(final_recall_score, 90)

    if question_type == "FACT" and keyword_score == 0:
        final_recall_score = min(final_recall_score, 49)

    return {
        "similarityScore": round(similarity_score, 2),
        "keywordScore": round(keyword_score, 2),
        "finalRecallScore": round(final_recall_score, 2),
        "textScore": round(similarity_score, 2),
    }


def calculate_final_risk_score(
    speech_risk_score: float,
    recall_score: float,
) -> dict:
    recall_risk_score = 100 - recall_score

    final_risk_score = (
        speech_risk_score * 0.3
        + recall_risk_score * 0.7
    )

    if final_risk_score < 30:
        risk_level = "LOW"
    elif final_risk_score < 60:
        risk_level = "MEDIUM"
    else:
        risk_level = "HIGH"

    return {
        "finalRiskScore": round(final_risk_score, 2),
        "riskLevel": risk_level,
    }
