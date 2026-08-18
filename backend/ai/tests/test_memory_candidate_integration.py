import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


import voice_reply_handler as handler  # noqa: E402
from recall.memory_candidate_service import (  # noqa: E402
    build_memory_candidate_selection,
)


class MemoryCandidateIntegrationTest(unittest.TestCase):
    def test_followup_details_share_event_but_keep_one_recall_target(self):
        records = [
            {
                "recordId": 10,
                "turnOrder": 1,
                "transcriptText": "김치볶음밥을 먹었어",
                "aiReplyText": "누구와 함께 먹었나요?",
            },
            {
                "recordId": 11,
                "turnOrder": 2,
                "transcriptText": "딸과 같이 먹었어",
                "aiReplyText": "어디에서 드셨나요?",
            },
            {
                "recordId": 12,
                "turnOrder": 3,
                "transcriptText": "집에서 먹었어",
                "aiReplyText": "오늘 보신 방송이 있나요?",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)

        self.assertEqual([10, 11, 12], [item["sourceRecordId"] for item in payloads])
        self.assertEqual("MEAL", payloads[0]["topic"])
        self.assertEqual("김치볶음밥", payloads[0]["answerValue"])
        self.assertEqual("딸", payloads[1]["answerValue"])
        self.assertEqual("집", payloads[2]["answerValue"])
        self.assertTrue(payloads[1]["continuesPreviousEvent"])
        self.assertTrue(payloads[2]["continuesPreviousEvent"])

        candidate = build_memory_candidate_selection(payloads).to_dict()[
            "candidates"
        ][0]
        self.assertEqual("MEAL:10", candidate["eventId"])
        self.assertEqual([10, 11, 12], candidate["sourceRecordIds"])
        self.assertEqual(
            ["김치볶음밥을 먹었어", "딸과 같이 먹었어", "집에서 먹었어"],
            [item["sourceText"] for item in candidate["evidence"]],
        )
        self.assertEqual(
            {
                "FOOD": ["김치볶음밥"],
                "PERSON": ["딸"],
                "PLACE": ["집"],
            },
            candidate["answerValues"],
        )

    def test_structured_context_returns_original_text_and_record_id(self):
        records = [
            {
                "recordId": 20,
                "turnOrder": 1,
                "transcriptText": "김치볶음밥을 먹었어",
                "aiReplyText": "누구와 함께 먹었나요?",
            },
            {
                "recordId": 21,
                "turnOrder": 2,
                "transcriptText": "딸과 같이 먹었어",
                "aiReplyText": "오늘 보신 방송이 있나요?",
            },
            {
                "recordId": 30,
                "turnOrder": 3,
                "transcriptText": "저녁에 뉴스를 봤어",
                "aiReplyText": "무슨 내용이 기억나세요?",
            },
            {
                "recordId": 31,
                "turnOrder": 4,
                "transcriptText": "정치 소식이었어",
                "aiReplyText": "오늘 기분은 어떠세요?",
            },
            {
                "recordId": 40,
                "turnOrder": 5,
                "transcriptText": "잘 모르겠어",
                "aiReplyText": "",
            },
        ]

        context = handler._get_structured_recall_ready_context(records)

        self.assertGreaterEqual(len(context.conversation_history), 2)
        self.assertEqual(
            len(context.conversation_history),
            len(context.memory_candidates),
        )
        for source_text in context.conversation_history:
            source_record_id = context.find_source_record_id(source_text)
            self.assertIsNotNone(source_record_id)
            self.assertIn(
                source_text,
                [
                    record["transcriptText"]
                    for record in records
                    if record["recordId"] == source_record_id
                ],
            )

        for candidate in context.memory_candidates:
            self.assertEqual(
                candidate["sourceRecordId"],
                context.find_source_record_id(candidate["sourceText"]),
            )
            self.assertTrue(candidate["eventId"])
            self.assertTrue(candidate["answerType"])
            self.assertTrue(candidate["answerValue"])

    def test_cross_slot_followup_stays_in_event_but_new_topic_does_not(self):
        records = [
            {
                "recordId": 50,
                "turnOrder": 1,
                "transcriptText": "공원에 다녀왔어",
                "aiReplyText": "누구와 함께 가셨어요?",
            },
            {
                "recordId": 51,
                "turnOrder": 2,
                "transcriptText": "딸이랑 갔어",
                "aiReplyText": "오늘 보신 방송이 있나요?",
            },
            {
                "recordId": 52,
                "turnOrder": 3,
                "transcriptText": "저녁 뉴스를 봤어",
                "aiReplyText": "무슨 내용이 기억나세요?",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)

        self.assertEqual("PLACE", payloads[0]["topic"])
        self.assertEqual("PLACE", payloads[1]["topic"])
        self.assertTrue(payloads[1]["continuesPreviousEvent"])
        self.assertEqual("MEDIA", payloads[2]["topic"])
        self.assertFalse(payloads[2]["continuesPreviousEvent"])

    def test_low_information_answer_never_becomes_confirmed_candidate(self):
        records = [
            {
                "recordId": 60,
                "turnOrder": 1,
                "transcriptText": "아직은 없어",
                "aiReplyText": "오늘 기억에 남는 일이 있으셨어요?",
            },
            {
                "recordId": 61,
                "turnOrder": 2,
                "transcriptText": "없다니까",
                "aiReplyText": "요즘 즐겨 보는 방송이 있으세요?",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertTrue(all(not item["answerValue"] for item in payloads))
        self.assertEqual([], result["candidates"])

    def test_untyped_followups_keep_one_meal_event(self):
        records = [
            {
                "recordId": 70,
                "turnOrder": 1,
                "transcriptText": "엽기 떡볶이와 치킨을 먹었어",
                "aiReplyText": "드셨을 때 맛은 어떠셨어요?",
            },
            {
                "recordId": 71,
                "turnOrder": 2,
                "transcriptText": "맛있었어",
                "aiReplyText": "같이 드신 분이 계셨어요?",
            },
            {
                "recordId": 72,
                "turnOrder": 3,
                "transcriptText": "혼자 먹었어",
                "aiReplyText": "그 음식은 어디에서 드셨어요?",
            },
            {
                "recordId": 73,
                "turnOrder": 4,
                "transcriptText": "집에서 먹었어",
                "aiReplyText": "식사 후에는 뭘 하셨어요?",
            },
            {
                "recordId": 74,
                "turnOrder": 5,
                "transcriptText": "식사 후에는 잤어",
                "aiReplyText": "오늘 보신 방송이 있으세요?",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertEqual(1, len(result["candidates"]))
        candidate = result["candidates"][0]
        self.assertEqual("MEAL:70", candidate["eventId"])
        self.assertEqual([70, 71, 72, 73, 74], candidate["sourceRecordIds"])
        self.assertEqual(
            {
                "FOOD": ["엽기 떡볶이와 치킨"],
                "PERSON": ["혼자"],
                "PLACE": ["집"],
                "TIME": ["식사 후"],
            },
            candidate["answerValues"],
        )

    def test_correction_turn_updates_the_same_event(self):
        records = [
            {
                "recordId": 80,
                "turnOrder": 1,
                "transcriptText": "공원에 갔어",
                "aiReplyText": "누구와 같이 가셨어요?",
            },
            {
                "recordId": 81,
                "turnOrder": 2,
                "transcriptText": "딸과 갔어",
                "aiReplyText": "딸과 함께 가셨군요.",
            },
            {
                "recordId": 82,
                "turnOrder": 3,
                "transcriptText": "아니, 아들과 갔어",
                "aiReplyText": "그곳에서 무엇을 하셨어요?",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertEqual(1, len(result["candidates"]))
        candidate = result["candidates"][0]
        self.assertEqual([80, 81, 82], candidate["sourceRecordIds"])
        self.assertEqual(["아들"], candidate["answerValues"]["PERSON"])

    def test_real_meal_session_keeps_concrete_clues_in_one_event(self):
        records = [
            {
                "recordId": 107,
                "turnOrder": 1,
                "answerRole": "FIXED",
                "transcriptText": "김용희",
                "aiReplyText": "좋아요, 그럼 오늘 하루는 뭘 하면서 지내셨는지 이야기해주시겠어요?",
            },
            {
                "recordId": 108,
                "turnOrder": 2,
                "transcriptText": "김치볶음밥을 먹었어.",
                "aiReplyText": "그 음식은 어디에서 드셨어요?",
            },
            {
                "recordId": 109,
                "turnOrder": 3,
                "transcriptText": "집에서 먹었어.",
                "aiReplyText": "그때 누구와 같이 드셨어요?",
            },
            {
                "recordId": 110,
                "turnOrder": 4,
                "transcriptText": "딸과 같이 먹었어.",
                "aiReplyText": "밥 먹으면서 무슨 이야기 나누셨어요?",
            },
            {
                "recordId": 111,
                "turnOrder": 5,
                "transcriptText": "일상 얘기했어.",
                "aiReplyText": "딸이랑 밥 드시면서 분위기가 어땠어요?",
            },
            {
                "recordId": 112,
                "turnOrder": 6,
                "transcriptText": "분위기는 좋았어.",
                "aiReplyText": "그 음식을 떠올리면 제일 먼저 생각나는 게 무엇인가요?",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertEqual("FOOD", payloads[0]["answerType"])
        self.assertEqual("김치볶음밥", payloads[0]["answerValue"])
        self.assertEqual([], payloads[3]["clues"])
        self.assertEqual([], payloads[4]["clues"])
        self.assertEqual(1, len(result["candidates"]))
        candidate = result["candidates"][0]
        self.assertEqual("MEAL:108", candidate["eventId"])
        self.assertEqual([108, 109, 110, 111, 112], candidate["sourceRecordIds"])
        self.assertEqual(
            {
                "FOOD": ["김치볶음밥"],
                "PLACE": ["집"],
                "PERSON": ["딸"],
            },
            candidate["answerValues"],
        )

    def test_activity_followups_keep_person_and_time_in_one_event(self):
        records = [
            {
                "recordId": 120,
                "turnOrder": 1,
                "transcriptText": "공원에서 산책했어",
                "aiReplyText": "누구와 같이 가셨어요?",
            },
            {
                "recordId": 121,
                "turnOrder": 2,
                "transcriptText": "친구와 갔어",
                "aiReplyText": "언제 다녀오셨어요?",
            },
            {
                "recordId": 122,
                "turnOrder": 3,
                "transcriptText": "오전에 갔어",
                "aiReplyText": "",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertTrue(payloads[1]["continuesPreviousEvent"])
        self.assertTrue(payloads[2]["continuesPreviousEvent"])
        self.assertEqual("PERSON", payloads[1]["answerType"])
        self.assertEqual("TIME", payloads[2]["answerType"])
        self.assertEqual(1, len(result["candidates"]))
        candidate = result["candidates"][0]
        self.assertEqual("ACTIVITY:120", candidate["eventId"])
        self.assertEqual([120, 121, 122], candidate["sourceRecordIds"])
        self.assertEqual(
            {
                "PLACE": ["공원"],
                "ACTIVITY": ["산책"],
                "PERSON": ["친구"],
                "TIME": ["오전"],
            },
            candidate["answerValues"],
        )

    def test_followup_can_refine_an_object_clue_in_the_same_event(self):
        records = [
            {
                "recordId": 130,
                "turnOrder": 1,
                "transcriptText": "어제 손주에게 선물을 줬어",
                "aiReplyText": "무슨 선물을 주셨어요?",
            },
            {
                "recordId": 131,
                "turnOrder": 2,
                "transcriptText": "장난감을 줬어",
                "aiReplyText": "손주 반응은 어땠어요?",
            },
            {
                "recordId": 132,
                "turnOrder": 3,
                "transcriptText": "아주 좋아했어",
                "aiReplyText": "",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertTrue(payloads[1]["continuesPreviousEvent"])
        self.assertEqual("OBJECT", payloads[1]["answerType"])
        self.assertEqual("장난감", payloads[1]["answerValue"])
        self.assertEqual(1, len(result["candidates"]))
        candidate = result["candidates"][0]
        self.assertEqual("PERSON:130", candidate["eventId"])
        self.assertEqual([130, 131, 132], candidate["sourceRecordIds"])
        self.assertEqual(
            {
                "TIME": ["어제"],
                "PERSON": ["손주"],
                "OBJECT": ["선물", "장난감"],
            },
            candidate["answerValues"],
        )

    def test_media_answer_starts_a_new_event_after_a_meal(self):
        records = [
            {
                "recordId": 134,
                "turnOrder": 1,
                "transcriptText": "오늘 집에서 토스트를 먹었어.",
                "aiReplyText": "그때 누구와 같이 드셨어요?",
            },
            {
                "recordId": 135,
                "turnOrder": 2,
                "transcriptText": "나 혼자 먹었어",
                "aiReplyText": "드셨을 때 맛은 어떠셨어요?",
            },
            {
                "recordId": 136,
                "turnOrder": 3,
                "transcriptText": "정말 맛있었어",
                "aiReplyText": (
                    "토스트 드시면서 뭔가 보거나 들으신 게 있으세요?"
                ),
            },
            {
                "recordId": 137,
                "turnOrder": 4,
                "transcriptText": "유튜브 봤어",
                "aiReplyText": "유튜브에서 어떤 걸 보셨어요?",
            },
            {
                "recordId": 138,
                "turnOrder": 5,
                "transcriptText": "리셋을 봤어",
                "aiReplyText": "그 음식은 어디에서 드셨어요?",
            },
            {
                "recordId": 139,
                "turnOrder": 6,
                "transcriptText": "토스트는 집에서 먹었어.",
                "aiReplyText": "",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        by_record_id = {
            payload["sourceRecordId"]: payload
            for payload in payloads
        }

        self.assertEqual("MEDIA", by_record_id[137]["topic"])
        self.assertEqual("MEDIA", by_record_id[137]["answerType"])
        self.assertEqual("유튜브", by_record_id[137]["answerValue"])
        self.assertFalse(by_record_id[137]["continuesPreviousEvent"])

        self.assertEqual("MEDIA", by_record_id[138]["topic"])
        self.assertEqual("MEDIA", by_record_id[138]["answerType"])
        self.assertEqual("리셋", by_record_id[138]["answerValue"])
        self.assertTrue(by_record_id[138]["continuesPreviousEvent"])

        self.assertEqual("MEAL", by_record_id[139]["topic"])
        self.assertEqual("PLACE", by_record_id[139]["answerType"])
        self.assertEqual("집", by_record_id[139]["answerValue"])
        self.assertFalse(by_record_id[139]["continuesPreviousEvent"])

    def test_explicit_question_slot_wins_during_topic_transition(self):
        records = [
            {
                "recordId": 140,
                "turnOrder": 1,
                "transcriptText": "밥을 먹었어",
                "aiReplyText": "어디에 다녀오셨어요?",
            },
            {
                "recordId": 141,
                "turnOrder": 2,
                "transcriptText": "아들과 시장에 갔어",
                "aiReplyText": "",
            },
        ]

        payloads = handler._build_memory_evidence_payloads(records)
        answer = payloads[-1]

        self.assertEqual("PLACE", answer["topic"])
        self.assertEqual("PLACE", answer["answerType"])
        self.assertEqual("시장", answer["answerValue"])
        self.assertEqual(
            [
                {"answerType": "PERSON", "answerValue": "아들"},
                {"answerType": "PLACE", "answerValue": "시장"},
            ],
            answer["clues"],
        )
        self.assertFalse(answer["continuesPreviousEvent"])


if __name__ == "__main__":
    unittest.main()
