import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


import voice_reply_handler as handler  # noqa: E402


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
        self.assertTrue(payloads[1]["continuesPreviousEvent"])
        self.assertTrue(payloads[2]["continuesPreviousEvent"])

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


if __name__ == "__main__":
    unittest.main()
