import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


from recall.memory_candidate_service import (  # noqa: E402
    build_memory_candidate_selection,
)


class MemoryCandidateServiceTest(unittest.TestCase):
    def test_builds_grouped_candidates_from_analysis_payloads(self):
        result = build_memory_candidate_selection(
            [
                {
                    "sourceRecordId": 10,
                    "sourceText": "김치볶음밥 먹었어",
                    "topic": "MEAL",
                    "answerType": "FOOD",
                    "answerKeyword": "김치볶음밥",
                    "qualityScore": 75,
                },
                {
                    "sourceRecordId": 11,
                    "sourceText": "딸이랑 먹었어",
                    "topic": "MEAL",
                    "answerType": "PERSON",
                    "answerKeyword": "딸",
                    "qualityScore": 60,
                },
                {
                    "sourceRecordId": 12,
                    "sourceText": "집에서 먹었어",
                    "topic": "MEAL",
                    "answerType": "PLACE",
                    "answerKeyword": "집",
                    "qualityScore": 60,
                },
                {
                    "sourceRecordId": 20,
                    "sourceText": "뉴스를 봤어",
                    "topic": "MEDIA",
                    "answerType": "MEDIA",
                    "answerKeyword": "뉴스",
                    "qualityScore": 70,
                },
            ]
        ).to_dict()

        self.assertEqual(2, len(result["candidates"]))
        self.assertEqual([10, 11, 12], result["candidates"][0]["sourceRecordIds"])
        self.assertEqual(
            [
                "김치볶음밥 먹었어",
                "딸이랑 먹었어",
                "집에서 먹었어",
            ],
            [
                item["sourceText"]
                for item in result["candidates"][0]["evidence"]
            ],
        )
        self.assertEqual(
            {
                "sourceRecordId": 10,
                "sourceText": "김치볶음밥 먹었어",
                "answerType": "FOOD",
                "answerValue": "김치볶음밥",
                "qualityScore": 75,
            },
            result["candidates"][0]["recallTarget"],
        )
        self.assertNotIn("sourceText", result["candidates"][0])
        self.assertEqual(
            {
                "FOOD": ["김치볶음밥"],
                "PERSON": ["딸"],
                "PLACE": ["집"],
            },
            result["candidates"][0]["answerValues"],
        )
        self.assertEqual([], result["rejected"])

    def test_reports_invalid_payload_without_blocking_valid_candidates(self):
        result = build_memory_candidate_selection(
            [
                {
                    "sourceRecordId": 30,
                    "sourceText": "아들과 통화했어",
                    "topic": "PERSON",
                    "answerType": "PERSON",
                    "answerKeyword": "아들",
                    "qualityScore": 80,
                },
                {
                    "sourceText": "공원에 다녀왔어",
                    "topic": "PLACE",
                    "qualityScore": 80,
                },
            ]
        ).to_dict()

        self.assertEqual(1, len(result["candidates"]))
        self.assertEqual(1, len(result["rejected"]))
        self.assertIn("sourceRecordId", result["rejected"][0]["reason"])

    def test_limits_selection_to_three_candidates(self):
        payloads = [
            {
                "sourceRecordId": record_id,
                "sourceText": text,
                "topic": topic,
                "answerType": answer_type,
                "answerKeyword": keyword,
                "qualityScore": score,
            }
            for record_id, text, topic, answer_type, keyword, score in (
                (40, "친구를 만났어", "PERSON", "PERSON", "친구", 70),
                (50, "공원에 다녀왔어", "PLACE", "PLACE", "공원", 75),
                (60, "국수를 먹었어", "MEAL", "FOOD", "국수", 80),
                (70, "뉴스를 봤어", "MEDIA", "MEDIA", "뉴스", 85),
            )
        ]

        result = build_memory_candidate_selection(payloads).to_dict()

        self.assertEqual(3, len(result["candidates"]))
        self.assertEqual(
            [50, 60, 70],
            [item["sourceRecordIds"][0] for item in result["candidates"]],
        )


if __name__ == "__main__":
    unittest.main()
