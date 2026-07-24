import argparse
import json
import sys
from pathlib import Path
import requests
import os
import logging

logger = logging.getLogger(__name__)

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

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")

def map_to_record_analysis_dto(result):
    """
    Python 분석 결과를 Java RecordAnalysisDTO 구조에 맞게 매핑한다.
    """
    speech_analysis = {
        "pauseCount": result.get("pause_count"),
        "totalPauseDuration": result.get("total_pause_duration"),
        "avgPauseDuration": result.get("avg_pause_duration"),
        "maxPauseDuration": result.get("max_pause_duration"),
        "rmsMean": result.get("rms_mean"),
        "rmsStd": result.get("rms_std"),
        "zcrMean": result.get("zcr_mean"),
        "zcrStd": result.get("zcr_std"),
        "spectralCentroidMean": result.get("spectral_centroid_mean"),
        "spectralCentroidStd": result.get("spectral_centroid_std"),
        "mfcc1Mean": result.get("mfcc_1_mean"),
        "mfcc2Mean": result.get("mfcc_2_mean"),
        "mfcc3Mean": result.get("mfcc_3_mean"),
        "mfcc4Mean": result.get("mfcc_4_mean"),
        "mfcc5Mean": result.get("mfcc_5_mean"),
        "mfcc6Mean": result.get("mfcc_6_mean"),
        "mfcc7Mean": result.get("mfcc_7_mean"),
        "mfcc8Mean": result.get("mfcc_8_mean"),
        "mfcc9Mean": result.get("mfcc_9_mean"),
        "mfcc10Mean": result.get("mfcc_10_mean"),
        "mfcc11Mean": result.get("mfcc_11_mean"),
        "mfcc12Mean": result.get("mfcc_12_mean"),
        "mfcc13Mean": result.get("mfcc_13_mean"),
        "mfcc1Std": result.get("mfcc_1_std"),
        "mfcc2Std": result.get("mfcc_2_std"),
        "mfcc3Std": result.get("mfcc_3_std"),
        "mfcc4Std": result.get("mfcc_4_std"),
        "mfcc5Std": result.get("mfcc_5_std"),
        "mfcc6Std": result.get("mfcc_6_std"),
        "mfcc7Std": result.get("mfcc_7_std"),
        "mfcc8Std": result.get("mfcc_8_std"),
        "mfcc9Std": result.get("mfcc_9_std"),
        "mfcc10Std": result.get("mfcc_10_std"),
        "mfcc11Std": result.get("mfcc_11_std"),
        "mfcc12Std": result.get("mfcc_12_std"),
        "mfcc13Std": result.get("mfcc_13_std"),
        "speechRate": result.get("speech_rate_word"),
        "articulationScore": result.get("articulation_rate_word"),
        "pronunciationStability": None,
        "responseLatency": result.get("response_latency"),
        "repetitionCount": None,
        "fillerCount": None,
        "dysarthriaSimilarityScore": result.get("dysarthria_similarity_score"),
        "distanceFromReference": result.get("distance_from_reference"),
        "speechAbnormalityLevel": result.get("speech_abnormality_level"),
        "speechAbnormalityScore": result.get("speech_abnormality_score"),
        "egemapsAvailable": result.get("egemaps_available"),
        "egemapsError": result.get("egemaps_error"),
        "f0SemitoneMean": result.get("f0_semitone_mean"),
        "f0SemitoneStddevNorm": result.get("f0_semitone_stddev_norm"),
        "jitterLocal": result.get("jitter_local"),
        "shimmerLocalDb": result.get("shimmer_local_db"),
        "hnrDb": result.get("hnr_db"),
        "voicedSegmentsPerSec": result.get("voiced_segments_per_sec"),
        "meanVoicedSegmentLength": result.get("mean_voiced_segment_length"),
        "meanUnvoicedSegmentLength": result.get("mean_unvoiced_segment_length"),
        "voiceBreakCount": result.get("voice_break_count"),
        "voiceBreakRatio": result.get("voice_break_ratio"),
    }

    text_analysis = {
        "wordCount": result.get("word_count"),
        "sentenceCount": result.get("sentence_count"),
        "avgSentenceLength": result.get("avg_sentence_length"),
        "lexicalDiversity": result.get("lexical_diversity"),
        "repeatedWordRatio": result.get("repetition_ratio"),
        "incompleteSentenceCount": None,
        "topicCoherenceScore": None,
        "slowSpeechFlag": result.get("slow_speech_flag"),
        "longRecordingFlag": result.get("long_recording_flag"),
        "lowContentSlowSpeechFlag": result.get("low_content_slow_speech_flag"),
    }

    return {
        "recordId": result.get("recordId"),
        "transcriptText": result.get("transcriptText"),
        "speechAnalysis": speech_analysis,
        "textAnalysis": text_analysis,
    }


def compact_speech_result(result):
    """
    Java 백엔드에서 보기 좋게 음성 분석 결과를 주요 값 중심으로 정리한다. (기존 호환성용)
    """
    dto = map_to_record_analysis_dto(result)
    flat = {
        "recordId": dto["recordId"],
        "sessionId": result.get("sessionId"),
        "transcriptText": dto["transcriptText"],
    }
    flat.update(dto["speechAnalysis"])
    flat.update(dto["textAnalysis"])
    flat["baselineSpeechScore"] = result.get("baseline_speech_score")
    flat["baselineSpeechLevel"] = result.get("baseline_speech_level")
    flat["baselineReasons"] = result.get("baseline_reasons")
    flat["audioAnalysisAvailable"] = result.get("audio_analysis_available")
    flat["audioAnalysisError"] = result.get("audio_analysis_error")
    flat["rawSpeechScore"] = result.get("raw_speech_score")
    flat["speechRiskScore"] = result.get("speechRiskScore")
    flat["speechHealthScore"] = result.get("speechHealthScore")
    flat["speechRiskLevel"] = result.get("speechRiskLevel")
    flat["audioPath"] = result.get("audioPath")
    flat["audioUrl"] = result.get("audioUrl")
    flat["resolvedAudioPath"] = result.get("resolvedAudioPath")
    return flat


def run_record_mode(args):
    """
    record 1개 음성/STT 분석 모드.
    Java 백엔드가 실제 recordId, sessionId, STT 결과, durationSec를 넘길 때 사용한다.
    """
    import os
    logger.info("run_record_mode start recordId=%s audio_path=%s audio_url=%s",
                args.record_id, args.audio_path, args.audio_url)

    if args.audio_path:
        logger.info("audio_path exists=%s path=%s", os.path.exists(args.audio_path), args.audio_path)

    result = analyze_user_turn(
        record_id=args.record_id,
        session_id=args.session_id,
        audio_path=args.audio_path,
        audio_url=args.audio_url,
        transcript_text=args.transcript_text,
        duration_sec=args.duration_sec,
    )

    logger.info("analyze_user_turn done recordId=%s audio_analysis_available=%s",
                args.record_id, result.get("audio_analysis_available"))
    logger.info("speech_abnormality_score=%s dysarthria_similarity_score=%s",
                result.get("speech_abnormality_score"), result.get("dysarthria_similarity_score"))

    # Java DTO 구조에 맞게 매핑
    record_analysis_dto = map_to_record_analysis_dto(result)

    response = {
        "success": True,
        "recordId": args.record_id,
        "sessionId": args.session_id,
        "recordAnalysis": record_analysis_dto,
        # "finalRiskResult": { ... } <-- 이 부분을 제거하여 단일 턴 분석 시 0점인 채로 Spring에 덮어쓰지 않도록 합니다.
    }

    # 🔥 추가: 분석 완료 직후 Spring으로 결과를 자동 전송
    send_analysis_to_spring(response)

    return response

def send_analysis_to_spring(result_dict):
    if not result_dict or result_dict.get("success") is False:
        return

    try:
        if "recordAnalysis" in result_dict and result_dict["recordAnalysis"]:
            record_url = f"{SPRING_BASE_URL}/api/ai/analysis/record"

            print("===== RECORD PAYLOAD =====")
            print(json.dumps(result_dict["recordAnalysis"], indent=2, ensure_ascii=False))

            res_record = requests.post(
                record_url,
                json=result_dict["recordAnalysis"],
                timeout=10
            )

            print("RECORD STATUS =", res_record.status_code)
            print("RECORD RESPONSE =", res_record.text)

            res_record.raise_for_status()

        if "finalRiskResult" in result_dict and result_dict["finalRiskResult"]:
            risk_url = f"{SPRING_BASE_URL}/api/ai/analysis/risk"

            print("===== RISK PAYLOAD =====")
            print(json.dumps(result_dict["finalRiskResult"], indent=2, ensure_ascii=False))

            res_risk = requests.post(
                risk_url,
                json=result_dict["finalRiskResult"],
                timeout=10
            )

            print("RISK STATUS =", res_risk.status_code)
            print("RISK RESPONSE =", res_risk.text)

            res_risk.raise_for_status()

    except Exception as e:
        print(f"> [Python -> Spring] 분석 결과 전송 실패: {e}")

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
    # RiskAnalysisDTO 기준:
    # - speechScore: 세션 음성 위험 점수
    # - textScore: 회상 모델의 순수 텍스트 의미 유사도 점수
    # - recallScore: 키워드/질문유형 가중치까지 반영한 최종 회상 점수
    speech_score = speech_summary.get("speechRiskScore", 0.0)
    text_score = recall_scores.get("textScore", recall_scores["similarityScore"])
    recall_score = recall_scores["finalRecallScore"]

    risk_scores = calculate_final_risk_score(
        speech_risk_score=speech_score,
        recall_score=recall_score,
    )

    risk_analysis_dto = {
        "sessionId": session_id,
        "speechScore": speech_score,
        "textScore": text_score,
        "recallScore": recall_score,
        "finalRiskScore": risk_scores["finalRiskScore"],
        "riskLevel": risk_scores["riskLevel"],
    }

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
        "riskAnalysisDto": risk_analysis_dto,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["record", "full_dummy"], default="record")
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

        print(json.dumps(response, ensure_ascii=False, indent=2))

    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}))


if __name__ == "__main__":
    main()
