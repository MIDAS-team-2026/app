"""
회상 일치도 분석 프로토타입

- 과거 답변과 현재 답변의 의미 유사도 계산
- 질문별 핵심 키워드 일치도 계산
- 질문 유형별 가중치 적용
- 최종 회상 일치도 점수 산출
- 종합 위험도 계산에 사용할 recall_score 생성

실행:
    python prototype/recall_score_prototype.py

주의:
    실제 서비스 연동 시 DB 조회/저장 부분은 Spring Boot API 또는 DB Repository와 연결해야 한다.
"""

from __future__ import annotations

import random
import re
from datetime import datetime
from typing import Dict, List, Optional, Tuple

import pandas as pd
from sentence_transformers import SentenceTransformer, util


# =====================================================
# 1. 임시 DB 테이블
# =====================================================

users: List[Dict] = []
audio_records: List[Dict] = []
speech_analysis_results: List[Dict] = []
text_analysis_results: List[Dict] = []
recall_questions: List[Dict] = []
recall_answers: List[Dict] = []
recall_keywords: List[Dict] = []
recall_analysis_results: List[Dict] = []
risk_analysis_results: List[Dict] = []


# =====================================================
# 2. 모델 로드
# =====================================================

embedding_model = SentenceTransformer("jhgan/ko-sroberta-multitask")


# =====================================================
# 3. 공통 함수
# =====================================================

def now() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def clean_text(text: str) -> str:
    text = str(text)
    text = re.sub(r"[^가-힣a-zA-Z0-9\s]", "", text)
    return text.strip()


def next_id(table: List[Dict]) -> int:
    return len(table) + 1


# =====================================================
# 4. 사용자 및 회상 질문 생성
# =====================================================

def create_user(name: str, birth_year: int) -> Dict:
    user = {
        "user_id": next_id(users),
        "name": name,
        "birth_year": birth_year,
        "created_at": now(),
    }
    users.append(user)
    return user


def create_recall_question(
    user_id: int,
    question_text: str,
    question_type: str,
    category: str,
    keywords: List[str],
) -> Dict:
    question_id = next_id(recall_questions)

    question = {
        "recall_question_id": question_id,
        "user_id": user_id,
        "question_text": question_text,
        "question_type": question_type,
        "category": category,
        "created_at": now(),
    }

    recall_questions.append(question)

    for keyword in keywords:
        recall_keywords.append({
            "keyword_id": next_id(recall_keywords),
            "recall_question_id": question_id,
            "keyword_text": keyword,
        })

    return question


def create_audio_record(
    user_id: int,
    transcript_text: str,
    audio_duration: Optional[float] = None,
    audio_file_path: Optional[str] = None,
    stt_confidence: Optional[float] = None,
) -> Dict:
    record_id = next_id(audio_records)

    if audio_duration is None:
        audio_duration = round(random.uniform(5.0, 40.0), 2)

    if audio_file_path is None:
        audio_file_path = f"/audio/user{user_id}/record_{record_id:03d}.wav"

    record = {
        "record_id": record_id,
        "user_id": user_id,
        "audio_file_path": audio_file_path,
        "audio_duration": audio_duration,
        "transcript_text": transcript_text,
        "stt_confidence": stt_confidence,
        "recorded_at": now(),
    }

    audio_records.append(record)
    return record


def create_recall_answer(
    user_id: int,
    recall_question_id: int,
    answer_type: str,
    transcript_text: str,
) -> Dict:
    audio_record = create_audio_record(
        user_id=user_id,
        transcript_text=transcript_text,
        stt_confidence=round(random.uniform(0.85, 0.99), 2),
    )

    answer = {
        "recall_answer_id": next_id(recall_answers),
        "recall_question_id": recall_question_id,
        "user_id": user_id,
        "record_id": audio_record["record_id"],
        "answer_type": answer_type,
        "created_at": now(),
    }

    recall_answers.append(answer)
    return answer


# =====================================================
# 5. 조회 함수
# =====================================================

def get_transcript_by_record_id(record_id: int) -> Optional[str]:
    for record in audio_records:
        if record["record_id"] == record_id:
            return record["transcript_text"]
    return None


def get_question_by_id(question_id: int) -> Optional[Dict]:
    for question in recall_questions:
        if question["recall_question_id"] == question_id:
            return question
    return None


def get_keywords_by_question_id(question_id: int) -> List[str]:
    return [
        keyword["keyword_text"]
        for keyword in recall_keywords
        if keyword["recall_question_id"] == question_id
    ]


# =====================================================
# 6. 회상 일치도 계산
# =====================================================

def get_recall_weights(question_type: str) -> Tuple[float, float]:
    weights = {
        "FACT": (0.2, 0.8),
        "PREFERENCE": (0.4, 0.6),
        "MEMORY": (0.6, 0.4),
        "DAILY": (0.3, 0.7),
    }
    return weights.get(question_type, (0.4, 0.6))


def calculate_similarity_score(past_text: str, current_text: str) -> float:
    emb1 = embedding_model.encode(clean_text(past_text), convert_to_tensor=True)
    emb2 = embedding_model.encode(clean_text(current_text), convert_to_tensor=True)
    score = util.cos_sim(emb1, emb2).item()
    return max(0.0, score * 100)


def calculate_keyword_score(keywords: List[str], current_text: str) -> float:
    current_text = clean_text(current_text)

    if not keywords:
        return 0.0

    matched = 0
    for keyword in keywords:
        if keyword in current_text:
            matched += 1

    return matched / len(keywords) * 100


def analyze_recall_for_question(user_id: int, question_id: int) -> Optional[Dict]:
    question = get_question_by_id(question_id)
    if question is None:
        return None

    keywords = get_keywords_by_question_id(question_id)

    initial_answer = None
    recall_answer = None

    for answer in recall_answers:
        if answer["user_id"] == user_id and answer["recall_question_id"] == question_id:
            if answer["answer_type"] == "INITIAL":
                initial_answer = answer
            elif answer["answer_type"] == "RECALL":
                recall_answer = answer

    if initial_answer is None or recall_answer is None:
        return None

    past_text = get_transcript_by_record_id(initial_answer["record_id"])
    current_text = get_transcript_by_record_id(recall_answer["record_id"])

    if past_text is None or current_text is None:
        return None

    similarity_score = calculate_similarity_score(past_text, current_text)
    keyword_score = calculate_keyword_score(keywords, current_text)

    similarity_weight, keyword_weight = get_recall_weights(question["question_type"])
    final_recall_score = (
        similarity_score * similarity_weight
        + keyword_score * keyword_weight
    )

    # FACT 질문은 정답 키워드의 영향이 크도록 보정한다.
    if question["question_type"] == "FACT" and keyword_score == 100:
        final_recall_score = max(final_recall_score, 90)

    if question["question_type"] == "FACT" and keyword_score == 0:
        final_recall_score = min(final_recall_score, 49)

    result = {
        "recall_result_id": next_id(recall_analysis_results),
        "recall_question_id": question_id,
        "past_record_id": initial_answer["record_id"],
        "current_record_id": recall_answer["record_id"],
        "similarity_score": round(similarity_score, 2),
        "keyword_score": round(keyword_score, 2),
        "final_recall_score": round(final_recall_score, 2),
        "analyzed_at": now(),
    }

    recall_analysis_results.append(result)
    return result


def calculate_recall_score(user_id: int) -> Optional[float]:
    user_question_ids = [
        question["recall_question_id"]
        for question in recall_questions
        if question["user_id"] == user_id
    ]

    scores = [
        result["final_recall_score"]
        for result in recall_analysis_results
        if result["recall_question_id"] in user_question_ids
    ]

    if not scores:
        return None

    return sum(scores) / len(scores)


# =====================================================
# 7. 종합 위험도 계산
# =====================================================

def calculate_final_risk(
    user_id: int,
    speech_score: float = 70.0,
    text_score: float = 75.0,
) -> Dict:
    recall_score = calculate_recall_score(user_id)

    if recall_score is None:
        recall_score = 0.0

    # 정상 점수가 높을수록 양호하다고 보고, 위험도는 100 - health_score로 계산한다.
    health_score = (
        speech_score * 0.3
        + text_score * 0.2
        + recall_score * 0.5
    )
    final_risk_score = 100 - health_score

    if final_risk_score < 30:
        risk_level = "low"
    elif final_risk_score < 60:
        risk_level = "medium"
    else:
        risk_level = "high"

    result = {
        "risk_result_id": next_id(risk_analysis_results),
        "user_id": user_id,
        "speech_score": round(speech_score, 2),
        "text_score": round(text_score, 2),
        "recall_score": round(recall_score, 2),
        "final_risk_score": round(final_risk_score, 2),
        "risk_level": risk_level,
        "analyzed_at": now(),
    }

    risk_analysis_results.append(result)
    return result


# =====================================================
# 8. 실행 예시
# =====================================================

def run_demo() -> None:
    user = create_user("테스트사용자", 1945)
    user_id = user["user_id"]

    create_recall_question(
        user_id=user_id,
        question_text="고향이 어디인가요?",
        question_type="FACT",
        category="개인정보",
        keywords=["대구"],
    )
    create_recall_question(
        user_id=user_id,
        question_text="가장 좋아하는 음식은 무엇인가요?",
        question_type="PREFERENCE",
        category="취향",
        keywords=["김치찌개"],
    )
    create_recall_question(
        user_id=user_id,
        question_text="가장 기억에 남는 여행은 무엇인가요?",
        question_type="MEMORY",
        category="장기기억",
        keywords=["제주도", "가족"],
    )
    create_recall_question(
        user_id=user_id,
        question_text="오늘 아침에 무엇을 드셨나요?",
        question_type="DAILY",
        category="단기기억",
        keywords=["미역국", "밥"],
    )

    create_recall_answer(user_id, 1, "INITIAL", "제 고향은 대구입니다.")
    create_recall_answer(user_id, 2, "INITIAL", "김치찌개를 가장 좋아합니다.")
    create_recall_answer(user_id, 3, "INITIAL", "가족들과 제주도 여행을 갔던 기억이 가장 좋았습니다.")
    create_recall_answer(user_id, 4, "INITIAL", "오늘 아침에는 미역국과 밥을 먹었습니다.")

    create_recall_answer(user_id, 1, "RECALL", "저는 대구에서 태어났습니다.")
    create_recall_answer(user_id, 2, "RECALL", "김치찌개를 좋아합니다.")
    create_recall_answer(user_id, 3, "RECALL", "가족과 제주도에 갔던 기억이 좋았습니다.")
    create_recall_answer(user_id, 4, "RECALL", "아침에는 밥을 먹었던 것 같습니다.")

    for question in recall_questions:
        analyze_recall_for_question(user_id, question["recall_question_id"])

    risk_result = calculate_final_risk(user_id=user_id)

    print("[회상 분석 결과]")
    print(pd.DataFrame(recall_analysis_results))

    print("\n[최종 위험도 결과]")
    print(risk_result)


if __name__ == "__main__":
    run_demo()
