import re
from typing import List, Optional, Tuple

from sentence_transformers import SentenceTransformer, util


embedding_model = SentenceTransformer("jhgan/ko-sroberta-multitask")


def clean_text(text: str) -> str:
    text = str(text)
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def clamp_score(score: float) -> float:
    return round(max(0.0, min(float(score), 100.0)), 2)


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
        if keyword in current_text:
            matched += 1

    return clamp_score((matched / len(cleaned_keywords)) * 100)


def calculate_final_recall_score(
    past_text: str,
    current_text: str,
    keywords: Optional[List[str]] = None,
    question_type: str = "DEFAULT",
) -> dict:
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