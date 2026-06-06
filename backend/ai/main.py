import argparse
import json
import sys
from pathlib import Path


# ==========================
# import 경로 설정
# ==========================

AI_ROOT = Path(__file__).resolve().parent
ANALYSIS_SRC = AI_ROOT / "analysis" / "src"
RECALL_ROOT = AI_ROOT / "recall"

sys.path.append(str(ANALYSIS_SRC))
sys.path.append(str(RECALL_ROOT))

from user_turn_analysis import analyze_user_turn
from session_speech_summary import summarize_session_speech

from recall_score_calculator import (
    calculate_final_recall_score,
    calculate_final_risk_score,
)


def compact_speech_result(result):
    """
    Java 백엔드에서 보기 좋게 음성 분석 결과를 주요 값 중심으로 정리한다.
    """

    return {
        "recordId": result.get("recordId"),
        "sessionId": result.get("sessionId"),
        "transcriptText": result.get("transcriptText"),

        "wordCount": result.get("word_count"),
        "charCount": result.get("char_count"),
        "speechRateWord": result.get("speech_rate_word"),
        "speechRateChar": result.get("speech_rate_char"),

        "shortAnswerFlag": result.get("short_answer_flag"),
        "slowSpeechFlag": result.get("slow_speech_flag"),
        "longRecordingFlag": result.get("long_recording_flag"),
        "lowContentSlowSpeechFlag": result.get("low_content_slow_speech_flag"),

        "baselineSpeechScore": result.get("baseline_speech_score"),
        "baselineSpeechLevel": result.get("baseline_speech_level"),
        "baselineReasons": result.get("baseline_reasons"),

        "audioAnalysisAvailable": result.get("audio_analysis_available"),
        "audioAnalysisError": result.get("audio_analysis_error"),

        "dysarthriaSimilarityScore": result.get("dysarthria_similarity_score"),
        "distanceFromReference": result.get("distance_from_reference"),
        "speechAbnormalityScore": result.get("speech_abnormality_score"),
        "speechAbnormalityLevel": result.get("speech_abnormality_level"),

        "rawSpeechScore": result.get("raw_speech_score"),
        "speechRiskScore": result.get("speechRiskScore"),
        "speechHealthScore": result.get("speechHealthScore"),
        "speechRiskLevel": result.get("speechRiskLevel"),

        "audioPath": result.get("audioPath"),
        "audioUrl": result.get("audioUrl"),
        "resolvedAudioPath": result.get("resolvedAudioPath"),
    }


def run_record_mode(args):
    """
    record 1개 음성/STT 분석 모드.
    Java 백엔드가 실제 recordId, sessionId, STT 결과, durationSec를 넘길 때 사용한다.
    """

    result = analyze_user_turn(
        record_id=args.record_id,
        session_id=args.session_id,
        audio_path=args.audio_path,
        audio_url=args.audio_url,
        transcript_text=args.transcript_text,
        duration_sec=args.duration_sec,
    )

    return {
        "success": True,
        "mode": "record",
        **compact_speech_result(result),
    }


def run_full_dummy_mode():
    """
    백엔드 연결 테스트용 전체 더미 파이프라인.

    흐름:
    1. INITIAL 답변 음성/STT 분석
    2. RECALL 답변 음성/STT 분석
    3. session_speech_summary로 세션 음성 점수 계산
    4. recall_score_calculator로 회상 점수 계산
    5. 기존 calculate_final_risk_score로 최종 위험도 계산
    """

    session_id = 1

    initial_text = "오늘 아침에는 미역국과 밥을 먹었어요"
    recall_text = "아침에는 밥을 먹었던 것 같아요"

    # 1. INITIAL record 분석
    initial_speech = analyze_user_turn(
        record_id=1,
        session_id=session_id,
        audio_path="",
        audio_url="",
        transcript_text=initial_text,
        duration_sec=5.5,
    )

    # 2. RECALL record 분석
    recall_speech = analyze_user_turn(
        record_id=2,
        session_id=session_id,
        audio_path="",
        audio_url="",
        transcript_text=recall_text,
        duration_sec=7.0,
    )

    # 3. 세션 음성 요약
    speech_summary = summarize_session_speech(
        session_id=session_id,
        turn_results=[
            initial_speech,
            recall_speech,
        ],
    )

    # 기존 recall_score_calculator.calculate_final_risk_score()는
    # speech_score를 높을수록 좋은 점수로 해석하므로
    # speechRiskScore를 health 방향으로 변환한다.
    session_speech_health_score = round(
        100.0 - speech_summary.get("speechRiskScore", 0.0),
        2,
    )

    # 4. 회상 점수 계산
    recall_scores = calculate_final_recall_score(
        past_text=initial_text,
        current_text=recall_text,
        keywords=["미역국", "밥"],
        question_type="DAILY",
    )

    # 5. 최종 위험도 계산
    risk_scores = calculate_final_risk_score(
        speech_score=session_speech_health_score,
        text_score=session_speech_health_score,
        recall_score=recall_scores["finalRecallScore"],
    )

    return {
        "success": True,
        "mode": "full_dummy",
        "sessionId": session_id,

        "initialSpeechResult": compact_speech_result(initial_speech),
        "recallSpeechResult": compact_speech_result(recall_speech),

        "speechSummary": speech_summary,
        "sessionSpeechHealthScore": session_speech_health_score,

        "recallResult": {
            "recallQuestionId": 1,
            "pastRecordId": 1,
            "currentRecordId": 2,
            **recall_scores,
        },

        "finalRiskResult": risk_scores,
    }


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--mode",
        choices=["record", "full_dummy"],
        default="record",
    )

    # record mode에서만 필수처럼 사용할 값들
    parser.add_argument("--record_id", type=int)
    parser.add_argument("--session_id", type=int)
    parser.add_argument("--audio_path", default="")
    parser.add_argument("--audio_url", default="")
    parser.add_argument("--transcript_text", default="")
    parser.add_argument("--duration_sec", type=float, default=0.0)

    args = parser.parse_args()

    try:
        if args.mode == "record":
            if args.record_id is None or args.session_id is None:
                raise ValueError("record mode에서는 --record_id와 --session_id가 필요합니다.")

            response = run_record_mode(args)

        elif args.mode == "full_dummy":
            response = run_full_dummy_mode()

        else:
            response = {
                "success": False,
                "error": f"지원하지 않는 mode입니다: {args.mode}",
            }

    except Exception as e:
        response = {
            "success": False,
            "mode": args.mode,
            "error": str(e),
        }

    print(json.dumps(response, ensure_ascii=False))


if __name__ == "__main__":
    main()