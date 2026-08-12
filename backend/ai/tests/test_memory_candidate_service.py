import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


from recall.memory_candidate_service import (  # noqa: E402
    build_memory_candidate_selection,
)


class MemoryCandidateServiceTest(unittest.TestCase):
    def test_excludes_candidate_without_confirmed_type_and_value(self):
        result = build_memory_candidate_selection(
            [
                {
                    "sourceRecordId": 1,
                    "sourceText": "그냥 쉬었어",
                    "topic": "ACTIVITY",
                    "answerType": "UNKNOWN",
                    "qualityScore": 90,
                }
            ]
        ).to_dict()

        self.assertEqual([], result["candidates"])

    def test_preserves_multiple_clues_from_one_source_record(self):
        result = build_memory_candidate_selection(
            [
                {
                    "sourceRecordId": 5,
                    "sourceText": "딸과 공원에서 산책했어",
                    "topic": "ACTIVITY",
                    "answerType": "ACTIVITY",
                    "answerValue": "산책",
                    "clues": [
                        {"answerType": "PERSON", "answerValue": "딸"},
                        {"answerType": "PLACE", "answerValue": "공원"},
                        {"answerType": "ACTIVITY", "answerValue": "산책"},
                    ],
                    "qualityScore": 90,
                }
            ]
        ).to_dict()

        candidate = result["candidates"][0]
        self.assertEqual([5], candidate["sourceRecordIds"])
        self.assertEqual(
            {
                "PERSON": ["딸"],
                "PLACE": ["공원"],
                "ACTIVITY": ["산책"],
            },
            candidate["answerValues"],
        )
        self.assertEqual(5, candidate["recallClue"]["sourceRecordId"])
        self.assertEqual(
            "딸과 공원에서 산책했어",
            candidate["recallClue"]["sourceText"],
        )
        self.assertEqual("ACTIVITY", candidate["recallClue"]["answerType"])
        self.assertEqual("산책", candidate["recallClue"]["answerValue"])
        self.assertTrue(
            candidate["recallClue"]["clueId"].startswith("ACTIVITY:5:ACTIVITY:")
        )

    def test_used_clue_selects_another_clue_from_the_same_event(self):
        payload = {
            "sourceRecordId": 6,
            "sourceText": "딸과 공원에서 산책했어",
            "topic": "ACTIVITY",
            "answerType": "ACTIVITY",
            "answerValue": "산책",
            "clues": [
                {"answerType": "PERSON", "answerValue": "딸"},
                {"answerType": "PLACE", "answerValue": "공원"},
                {"answerType": "ACTIVITY", "answerValue": "산책"},
            ],
            "qualityScore": 90,
        }
        initial = build_memory_candidate_selection([payload]).to_dict()
        used_clue_id = initial["candidates"][0]["recallClue"]["clueId"]

        result = build_memory_candidate_selection(
            [payload],
            used_clue_ids={used_clue_id},
        ).to_dict()

        candidate = result["candidates"][0]
        self.assertNotEqual(used_clue_id, candidate["recallClue"]["clueId"])
        self.assertEqual(
            "USED",
            next(
                clue["status"]
                for clue in candidate["recallClues"]
                if clue["clueId"] == used_clue_id
            ),
        )

    def test_event_is_excluded_after_all_of_its_clues_are_used(self):
        payload = {
            "sourceRecordId": 7,
            "sourceText": "공원에서 산책했어",
            "topic": "ACTIVITY",
            "answerType": "ACTIVITY",
            "answerValue": "산책",
            "clues": [
                {"answerType": "PLACE", "answerValue": "공원"},
                {"answerType": "ACTIVITY", "answerValue": "산책"},
            ],
            "qualityScore": 90,
        }
        initial = build_memory_candidate_selection([payload]).to_dict()
        used_clue_ids = {
            clue["clueId"]
            for clue in initial["candidates"][0]["recallClues"]
        }

        result = build_memory_candidate_selection(
            [payload],
            used_clue_ids=used_clue_ids,
        ).to_dict()

        self.assertEqual([], result["candidates"])

    def test_used_event_is_not_selected_again(self):
        payload = {
            "sourceRecordId": 15,
            "sourceText": "김치볶음밥을 먹었어",
            "topic": "MEAL",
            "answerType": "FOOD",
            "answerValue": "김치볶음밥",
            "qualityScore": 90,
        }

        result = build_memory_candidate_selection(
            [payload],
            used_event_ids={"MEAL:15"},
        ).to_dict()

        self.assertEqual([], result["candidates"])

    def test_later_correction_replaces_clue_without_splitting_event(self):
        result = build_memory_candidate_selection(
            [
                {
                    "sourceRecordId": 20,
                    "turnOrder": 1,
                    "sourceText": "딸과 공원에 갔어",
                    "topic": "ACTIVITY",
                    "answerType": "PERSON",
                    "answerValue": "딸",
                    "qualityScore": 80,
                },
                {
                    "sourceRecordId": 21,
                    "turnOrder": 2,
                    "sourceText": "아니, 아들과 갔어",
                    "topic": "ACTIVITY",
                    "answerType": "PERSON",
                    "answerValue": "아들",
                    "qualityScore": 80,
                    "continuesPreviousEvent": True,
                    "isCorrection": True,
                },
            ]
        ).to_dict()

        self.assertEqual(1, len(result["candidates"]))
        candidate = result["candidates"][0]
        self.assertEqual([20, 21], candidate["sourceRecordIds"])
        self.assertEqual({"PERSON": ["아들"]}, candidate["answerValues"])
        self.assertEqual(21, candidate["recallClue"]["sourceRecordId"])
        self.assertEqual(
            ["아들"],
            [clue["answerValue"] for clue in candidate["recallClues"]],
        )

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
                    "continuesPreviousEvent": True,
                },
                {
                    "sourceRecordId": 12,
                    "sourceText": "집에서 먹었어",
                    "topic": "MEAL",
                    "answerType": "PLACE",
                    "answerKeyword": "집",
                    "qualityScore": 60,
                    "continuesPreviousEvent": True,
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
        self.assertEqual("MEAL:10", result["candidates"][0]["eventId"])
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
                    "clues": [
                        {
                            "answerType": "FOOD",
                            "answerValue": "김치볶음밥",
                        }
                    ],
                    "qualityScore": 75,
                "continuesPreviousEvent": False,
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
