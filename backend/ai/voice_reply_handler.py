"""
음성 업로드 후 AI 답변 생성 및 회상 질문 저장 로직.

핵심 흐름:
1. 앱 첫 화면은 사용자가 먼저 말하는 구조이므로 첫 발화는 채점하지 않고 첫 고정 질문을 안내한다.
2. 초기 고정 질문은 FIXED로 저장한다.
3. FIXED / INITIAL / RECALL 답변은 새로운 memoryPoint 후보에서 제외한다.
4. 자유대화 답변이 3개 이상 쌓이면 회상 질문을 생성한다.
5. 회상 질문 답변은 RECALL로 저장하고, 이후 다시 자유대화로 복귀한다.
"""

import logging
import os
import requests

from recall.free_talk_question_generator import (
    generate_safe_followup_question,
    is_similar_to_previous_question,
)
from recall.conversation_recall_generator import generate_and_save_recall_question
from recall.recall_api_client import analyze_session_recall

logger = logging.getLogger(__name__)
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


FIXED_QUESTIONS = [
    {
        "questionText": "성함을 어떻게 불러드리면 될까요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "USER_NAME",
    },
    {
        "questionText": "배우자분 성함은 어떻게 되세요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "SPOUSE_NAME",
    },
    {
        "questionText": "고향은 어디세요?",
        "questionType": "PERSONAL",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "HOMETOWN",
    },
    {
        "questionText": "오늘이 무슨 요일인지 기억나시나요?",
        "questionType": "ORIENTATION",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "TODAY_WEEKDAY",
    },
    {
        "questionText": "오늘 날짜도 기억나세요?",
        "questionType": "ORIENTATION",
        "category": "INITIAL_FIXED",
        "expectedAnswer": "TODAY_DATE",
    },
]


SAFE_OPENING_QUESTIONS = [
    "요즘 제일 보고 싶은 사람은 누구세요?",
    "오늘 드신 것 중에 기억나는 음식이 있으세요?",
    "오늘 집 밖에 다녀오신 곳이 있으세요?",
    "오늘 집에서는 주로 어떻게 시간을 보내셨어요?",
    "최근에 본 방송이나 들은 노래 중 기억나는 게 있으세요?",
    "요즘 동네에서 자주 보이는 풍경이 있으세요?",
    "최근에 손에 자주 잡는 물건이 있으세요?",
    "요즘 날씨를 보면 떠오르는 일이 있으세요?",
    "예전에 자주 하시던 일 중에 요즘 생각나는 게 있으세요?",
    "최근에 시장이나 마트 이야기가 떠오른 적 있으세요?",
    "집 안에서 가장 오래 머무는 자리가 어디세요?",
    "요즘 하루 중 기다려지는 시간이 있으세요?",
]


AFTER_RECALL_OPENING_QUESTIONS = [
    "이어서 오늘 드신 것 중에 생각나는 음식이 있으세요?",
    "이번에는 오늘 집에서 하신 일 중에 하나 말씀해주실래요?",
    "이어서 오늘 만났거나 연락한 사람이 있으세요?",
    "이번에는 최근에 본 방송이나 들은 노래 이야기도 해볼까요?",
    "이어서 오늘 밖이나 창밖에서 본 것이 있으세요?",
    "이번에는 손에 잡았던 물건이나 하셨던 일이 있으세요?",
]


SAFE_STAGE_FALLBACK_QUESTIONS = {
    "DEEPEN": [
        "조금 더 말해주시면, 그때는 어디에 계셨어요?",
        "그때 혼자 계셨어요, 아니면 누군가와 같이 계셨어요?",
        "그때 모습 중에 제일 먼저 떠오르는 게 있으세요?",
        "그때가 하루 중 언제쯤이었는지 기억나세요?",
        "그때 주변에 보였던 것이 하나라도 떠오르세요?",
        "그 이야기를 하다 보니 또 생각나는 게 있으세요?",
    ],
    "ANCHOR": [
        "나중에 다시 떠올릴 만한 장면이 하나 있으세요?",
        "그때 함께 있던 사람이나 장소가 기억나세요?",
        "그때 가장 먼저 생각나는 모습이 있으세요?",
        "방금 이야기한 걸 한 가지 장면으로 말하면 어떤 모습일까요?",
        "그때 계셨던 곳 주변에 뭐가 있었나요?",
        "나중에 다시 이야기한다면 어떤 말로 떠올리면 좋을까요?",
    ],
}


TOPIC_CHANGE_ACKNOWLEDGEMENTS = [
    "아하, 그렇군요.",
    "그러셨군요.",
    "음, 그렇군요.",
]


RECALL_TRANSITION_ACKNOWLEDGEMENTS = [
    "아하, 그렇군요.",
    "그러셨군요.",
]


def _fetch_session_records(session_id: int) -> list[dict]:
    try:
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/voice/session/{session_id}/records",
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.error("세션 레코드 조회 실패: sessionId=%s, error=%s", session_id, e)
        return []


def _save_ai_reply(record_id: int, reply_text: str):
    payload = {
        "recordId": record_id,
        "replyText": reply_text,
    }

    resp = requests.post(
        f"{SPRING_BASE_URL}/api/voice/reply",
        json=payload,
        timeout=10,
    )
    resp.raise_for_status()


def _link_recall_question(
    record_id: int,
    recall_question_id: int,
    answer_role: str,
):
    try:
        payload = {
            "recordId": record_id,
            "recallQuestionId": recall_question_id,
            "answerRole": answer_role,
        }

        resp = requests.post(
            f"{SPRING_BASE_URL}/api/voice/recall-link",
            json=payload,
            timeout=10,
        )
        resp.raise_for_status()

        logger.info(
            "레코드 질문 연결 완료: recordId=%s questionId=%s role=%s",
            record_id,
            recall_question_id,
            answer_role,
        )

    except Exception as e:
        logger.error(
            "레코드 질문 연결 실패: recordId=%s error=%s",
            record_id,
            e,
        )


def _create_recall_question(
    user_id: int,
    question_text: str,
    question_type: str,
    category: str,
    expected_answer: str,
) -> dict | None:
    body = {
        "userId": user_id,
        "questionText": question_text,
        "questionType": question_type,
        "category": category,
        "expectedAnswer": expected_answer,
    }

    resp = requests.post(
        f"{SPRING_BASE_URL}/api/recall/questions",
        json=body,
        timeout=10,
    )
    resp.raise_for_status()
    return resp.json()


def _fetch_recall_questions(user_id: int) -> list[dict]:
    try:
        resp = requests.get(
            f"{SPRING_BASE_URL}/api/recall/questions/{user_id}",
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.error("회상 질문 조회 실패: userId=%s error=%s", user_id, e)
        return []


def _find_or_create_fixed_question(user_id: int, question_data: dict) -> dict | None:
    questions = _fetch_recall_questions(user_id)
    target_text = question_data["questionText"].strip()

    for question in questions:
        question_text = str(question.get("questionText") or "").strip()
        category = str(question.get("category") or "").strip()

        if question_text == target_text and category == "INITIAL_FIXED":
            return question

    return _create_recall_question(
        user_id=user_id,
        question_text=question_data["questionText"],
        question_type=question_data["questionType"],
        category=question_data["category"],
        expected_answer=question_data["expectedAnswer"],
    )


def _count_fixed_answers(session_records: list[dict]) -> int:
    return sum(
        1
        for record in session_records
        if str(record.get("answerRole") or "").upper() == "FIXED"
    )


def _extract_recall_candidate_transcripts(session_records: list[dict]) -> list[str]:
    """
    가장 최근 회상 사이클 이후의 자유대화 답변만 회상 질문 후보로 사용한다.

    제외 대상:
    - 초기 고정 질문이 끝나기 전 첫 인사/답변
    - FIXED: 초기 고정 질문 답변
    - INITIAL: 이미 회상 질문의 기준 답변으로 사용된 답변
    - RECALL: 회상 질문에 대한 답변

    INITIAL과 RECALL을 제외하지 않으면
    회상 질문 → 회상 답변 → 다시 회상 질문 후보
    형태의 순환 구조가 생길 수 있다.
    """
    transcripts = []
    sorted_records = sorted(
        session_records,
        key=lambda record: int(record.get("recordId") or 0),
    )

    boundary_record_id = 0
    fixed_count = 0

    for record in sorted_records:
        role = str(record.get("answerRole") or "").upper()
        record_id = int(record.get("recordId") or 0)

        if role == "FIXED":
            fixed_count += 1
            boundary_record_id = max(boundary_record_id, record_id)
            continue

        if role in {"INITIAL", "RECALL"}:
            boundary_record_id = max(boundary_record_id, record_id)

    if fixed_count < len(FIXED_QUESTIONS):
        return []

    for record in sorted_records:
        role = str(record.get("answerRole") or "").upper()
        record_id = int(record.get("recordId") or 0)
        text = str(record.get("transcriptText") or "").strip()

        if record_id <= boundary_record_id:
            continue

        if not text:
            continue

        if role in {"FIXED", "INITIAL", "RECALL"}:
            continue

        transcripts.append(text)

    return transcripts


def _normalize_question_text(text: str) -> str:
    return " ".join(str(text or "").split())


def _count_completed_recall_answers(session_records: list[dict] | None) -> int:
    return sum(
        1
        for record in (session_records or [])
        if str(record.get("answerRole") or "").upper() == "RECALL"
    )


def _normalize_text(text: str) -> str:
    return " ".join(str(text or "").split())


def _get_recent_ai_replies(session_records: list[dict] | None) -> set[str]:
    replies = set()

    for record in session_records or []:
        reply_text = _normalize_text(record.get("aiReplyText") or "")

        if reply_text:
            replies.add(reply_text)

    return replies


def _get_recent_ai_reply_list(session_records: list[dict] | None) -> list[str]:
    replies = []

    for record in session_records or []:
        reply_text = _normalize_text(record.get("aiReplyText") or "")

        if reply_text:
            replies.append(reply_text)

    return replies


def _get_recent_transcript_list(
    session_records: list[dict] | None,
    limit: int = 8,
) -> list[str]:
    transcripts = []

    for record in session_records or []:
        transcript_text = _normalize_text(record.get("transcriptText") or "")

        if transcript_text:
            transcripts.append(transcript_text)

    return transcripts[-limit:]


def _get_cycle_transcripts(session_records: list[dict] | None) -> list[str]:
    if not session_records:
        return []

    return _extract_recall_candidate_transcripts(session_records)


def _get_topic_openers() -> list[str]:
    return SAFE_OPENING_QUESTIONS


def _pick_non_repeated_question(
    candidates: list[str],
    used_questions: set[str],
    previous_questions: list[str] | None,
    start_index: int,
) -> str | None:
    if not candidates:
        return None

    previous_questions = previous_questions or []

    for offset in range(len(candidates)):
        question = candidates[(start_index + offset) % len(candidates)]

        if _normalize_text(question) in used_questions:
            continue

        if is_similar_to_previous_question(question, previous_questions):
            continue

        return question

    for offset in range(len(candidates)):
        question = candidates[(start_index + offset) % len(candidates)]

        if _normalize_text(question) not in used_questions:
            return question

    return None


def _with_topic_change_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
    index = len(session_records or []) % len(TOPIC_CHANGE_ACKNOWLEDGEMENTS)
    acknowledgement = TOPIC_CHANGE_ACKNOWLEDGEMENTS[index]
    return f"{acknowledgement} {question}"


def _get_latest_record(session_records: list[dict] | None) -> dict | None:
    records = [record for record in session_records or [] if record.get("recordId")]

    if not records:
        return None

    return max(
        records,
        key=lambda record: int(record.get("turnOrder") or record.get("recordId") or 0),
    )


def _is_after_recall_answer(session_records: list[dict] | None) -> bool:
    latest_record = _get_latest_record(session_records)

    if not latest_record:
        return False

    return str(latest_record.get("answerRole") or "").upper() == "RECALL"


def _with_recall_transition_acknowledgement(
    question: str,
    session_records: list[dict] | None,
) -> str:
    index = len(session_records or []) % len(RECALL_TRANSITION_ACKNOWLEDGEMENTS)
    acknowledgement = RECALL_TRANSITION_ACKNOWLEDGEMENTS[index]
    return f"{acknowledgement} {question}"


def _get_next_normal_question(
    candidate_count: int,
    session_records: list[dict] | None = None,
    latest_text: str = "",
) -> str:
    cycle_index = _count_completed_recall_answers(session_records)
    question_index = cycle_index + candidate_count
    used_questions = _get_recent_ai_replies(session_records)
    previous_questions = _get_recent_ai_reply_list(session_records)
    recent_context = previous_questions + _get_recent_transcript_list(session_records)
    cycle_texts = _get_cycle_transcripts(session_records)

    if candidate_count <= 0:
        after_recall_answer = _is_after_recall_answer(session_records)
        opening_candidates = (
            AFTER_RECALL_OPENING_QUESTIONS
            if after_recall_answer
            else _get_topic_openers()
        )
        opener_question = _pick_non_repeated_question(
            candidates=opening_candidates,
            used_questions=used_questions,
            previous_questions=recent_context,
            start_index=cycle_index,
        )

        if opener_question:
            if after_recall_answer:
                return _with_recall_transition_acknowledgement(
                    opener_question,
                    session_records,
                )

            return opener_question

    stage = "DEEPEN" if candidate_count == 1 else "ANCHOR"
    fallback_question = _pick_non_repeated_question(
        candidates=SAFE_STAGE_FALLBACK_QUESTIONS[stage],
        used_questions=used_questions,
        previous_questions=recent_context,
        start_index=question_index,
    )

    if fallback_question:
        generated_question = generate_safe_followup_question(
            conversation_history=cycle_texts + [latest_text],
            stage=stage,
            fallback_question=fallback_question,
            previous_questions=recent_context,
        )

        if generated_question.get("shouldChangeTopic"):
            opener_question = _pick_non_repeated_question(
                candidates=_get_topic_openers(),
                used_questions=used_questions,
                previous_questions=recent_context,
                start_index=cycle_index + 1,
            )

            if opener_question:
                return _with_topic_change_acknowledgement(
                    opener_question,
                    session_records,
                )

        return generated_question["nextQuestion"]

    opener_question = _pick_non_repeated_question(
        candidates=_get_topic_openers(),
        used_questions=used_questions,
        previous_questions=recent_context,
        start_index=question_index,
    )

    if opener_question:
        return opener_question

    return _get_topic_openers()[question_index % len(_get_topic_openers())]


def _get_current_transcript(
    session_records: list[dict],
    current_record_id: int,
    fallback_text: str = "",
) -> str:
    for record in session_records:
        if record.get("recordId") == current_record_id:
            return record.get("transcriptText") or fallback_text

    return fallback_text


def _with_current_answer_role(
    session_records: list[dict],
    current_record_id: int,
    answer_role: str,
    recall_question_id: int | None = None,
) -> list[dict]:
    updated_records = []
    found_current_record = False

    for record in session_records:
        updated_record = dict(record)

        if updated_record.get("recordId") == current_record_id:
            updated_record["answerRole"] = answer_role
            found_current_record = True

            if recall_question_id is not None:
                updated_record["recallQuestionId"] = recall_question_id

        updated_records.append(updated_record)

    if not found_current_record:
        updated_records.append(
            {
                "recordId": current_record_id,
                "transcriptText": "",
                "answerRole": answer_role,
                "recallQuestionId": recall_question_id,
                "parentRecordId": None,
            }
        )

    return updated_records


def _find_pending_recall_question_id(
    session_records: list[dict],
    current_record_id: int,
) -> int | None:
    """
    아직 답변되지 않은 회상 질문을 찾는다.
    INITIAL은 회상 질문의 기준 답변이고,
    같은 recallQuestionId를 가진 RECALL이 아직 없으면 현재 답변을 RECALL로 연결한다.
    """
    initial_question_ids = []
    recalled_question_ids = set()

    sorted_records = sorted(
        session_records,
        key=lambda record: int(record.get("recordId") or 0),
    )

    for record in sorted_records:
        role = str(record.get("answerRole") or "").upper()
        question_id = record.get("recallQuestionId")
        record_id = record.get("recordId")

        if question_id is None:
            continue

        question_id = int(question_id)

        if role == "RECALL":
            recalled_question_ids.add(question_id)

        if role == "INITIAL" and record_id != current_record_id:
            initial_question_ids.append(question_id)

    for question_id in reversed(initial_question_ids):
        if question_id not in recalled_question_ids:
            return question_id

    return None


def _is_first_user_turn(session_records: list[dict], current_record_id: int) -> bool:
    """
    앱 첫 화면은 AI가 먼저 질문하지 않고 사용자가 먼저 말하는 구조다.
    그래서 첫 발화는 FIXED 답변으로 찍지 않고 첫 고정 질문을 안내한다.
    """
    if len(session_records) != 1:
        return False

    only_record = session_records[0]
    return only_record.get("recordId") == current_record_id


def process_voice_reply(
    record_id: int,
    session_id: int,
    user_id: int,
    transcript_text: str = "",
    speech_risk_score: float = 0.0,
    run_realtime_analysis: bool = False,
):
    logger.info("AI 답변 및 매핑 시작: recordId=%s sessionId=%s", record_id, session_id)

    try:
        session_records = _fetch_session_records(session_id)

        if not session_records:
            session_records = [
                {
                    "recordId": record_id,
                    "transcriptText": transcript_text,
                    "answerRole": None,
                    "recallQuestionId": None,
                    "parentRecordId": None,
                }
            ]

        # 0. 세션 첫 발화는 답변으로 채점하지 않고 첫 질문만 제공한다.
        if _is_first_user_turn(session_records, record_id):
            _save_ai_reply(
                record_id=record_id,
                reply_text=FIXED_QUESTIONS[0]["questionText"],
            )
            logger.info("첫 사용자 발화 감지. 첫 고정 질문 제공.")
            return

        # 1. 직전 회상 질문에 대한 답변이면 RECALL로 연결하고 자유대화로 복귀한다.
        pending_recall_question_id = _find_pending_recall_question_id(
            session_records=session_records,
            current_record_id=record_id,
        )

        if pending_recall_question_id is not None:
            _link_recall_question(
                record_id=record_id,
                recall_question_id=pending_recall_question_id,
                answer_role="RECALL",
            )

            logger.info(
                "회상 질문 답변으로 매핑 완료: recordId=%s questionId=%s",
                record_id,
                pending_recall_question_id,
            )

            if run_realtime_analysis:
                try:
                    analyze_session_recall(
                        user_id=user_id,
                        session_id=session_id,
                        speech_risk_score=speech_risk_score,
                        base_url=SPRING_BASE_URL,
                    )
                except Exception as e:
                    logger.warning("실시간 회상 분석 건너뜀: %s", e)

            records_after_recall = _with_current_answer_role(
                session_records=session_records,
                current_record_id=record_id,
                answer_role="RECALL",
                recall_question_id=pending_recall_question_id,
            )

            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    0,
                    records_after_recall,
                    latest_text=_get_current_transcript(
                        records_after_recall,
                        record_id,
                        transcript_text,
                    ),
                ),
            )
            return

        # 2. 초기 고정 질문 답변 처리
        fixed_answer_count = _count_fixed_answers(session_records)

        if fixed_answer_count < len(FIXED_QUESTIONS):
            current_question = FIXED_QUESTIONS[fixed_answer_count]

            fixed_question = _find_or_create_fixed_question(
                user_id=user_id,
                question_data=current_question,
            )

            fixed_question_id = fixed_question.get("questionId") if fixed_question else None

            if fixed_question_id:
                _link_recall_question(
                    record_id=record_id,
                    recall_question_id=fixed_question_id,
                    answer_role="FIXED",
                )

            next_index = fixed_answer_count + 1

            if next_index < len(FIXED_QUESTIONS):
                _save_ai_reply(
                    record_id=record_id,
                    reply_text=FIXED_QUESTIONS[next_index]["questionText"],
                )

                logger.info("초기 고정 질문 제공 완료: nextIndex=%s", next_index)
                return

            logger.info("초기 고정 질문 5개 완료. 자유대화 단계로 전환.")

            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(0, session_records),
            )
            return

        # 3. 자유대화 답변 3턴 이상 쌓이면 회상 질문 생성
        updated_records = _fetch_session_records(session_id)

        if not updated_records:
            updated_records = session_records

        transcripts = _extract_recall_candidate_transcripts(updated_records)

        if len(transcripts) < 3:
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    len(transcripts),
                    updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
                ),
            )
            logger.info("자유대화 후보 부족: count=%s. 일반 질문 제공.", len(transcripts))
            return

        result = generate_and_save_recall_question(
            user_id=user_id,
            conversation_history=transcripts,
            base_url=SPRING_BASE_URL,
        )

        reply_text = result.get("question")
        saved_question = result.get("savedQuestion")
        memory_point = result.get("memoryPoint", "")

        if result.get("status") != "CREATED" or not reply_text:
            _save_ai_reply(
                record_id=record_id,
                reply_text=_get_next_normal_question(
                    len(transcripts),
                    updated_records,
                    latest_text=_get_current_transcript(
                        updated_records,
                        record_id,
                        transcript_text,
                    ),
                ),
            )
            logger.info("회상 질문 생성 실패 또는 SKIPPED. 일반 질문으로 대체: %s", result.get("reason"))
            return

        _save_ai_reply(
            record_id=record_id,
            reply_text=reply_text,
        )

        if saved_question:
            recall_question_id = saved_question.get("questionId")

            if recall_question_id:
                _link_recall_question(
                    record_id=record_id,
                    recall_question_id=recall_question_id,
                    answer_role="INITIAL",
                )

                logger.info(
                    "자연 회상 질문 저장 및 INITIAL 연결 완료: questionId=%s memoryPoint=%s",
                    recall_question_id,
                    memory_point,
                )

    except Exception as e:
        logger.exception(
            "process_voice_reply 파이프라인 처리 중 치명적 예외 발생: %s",
            str(e),
        )
