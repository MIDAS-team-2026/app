import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


from recall.memory_evidence_extractor import extract_memory_clues  # noqa: E402
from recall.memory_event import MemoryAnswerType  # noqa: E402


class MemoryEngineMatrixTest(unittest.TestCase):
    def test_person_place_activity_matrix_is_grounded(self):
        people = (
            ("딸과", "딸"),
            ("아들과", "아들"),
            ("친구와", "친구"),
            ("손주와", "손주"),
        )
        places = ("공원", "병원", "학교", "시장")
        activities = ("산책", "운동", "구경", "쇼핑")

        for person_text, person in people:
            for place in places:
                for activity in activities:
                    text = f"{person_text} {place}에서 {activity}했어"
                    clues = extract_memory_clues(
                        text,
                        MemoryAnswerType.ACTIVITY,
                    )
                    values = {
                        (clue["answerType"], clue["answerValue"])
                        for clue in clues
                    }

                    with self.subTest(text=text):
                        self.assertIn(("PERSON", person), values)
                        self.assertIn(("PLACE", place), values)
                        self.assertIn(("ACTIVITY", activity), values)
                        self.assertTrue(
                            all(
                                clue["answerValue"] in text
                                for clue in clues
                            )
                        )

    def test_food_combinations_do_not_create_person_clues(self):
        foods = (
            ("김치볶음밥과 계란을", "김치볶음밥과 계란"),
            ("떡볶이와 치킨을", "떡볶이와 치킨"),
            ("라면과 김밥을", "라면과 김밥"),
            ("사과와 바나나를", "사과와 바나나"),
        )

        for food_text, food in foods:
            text = f"{food_text} 먹었어"
            clues = extract_memory_clues(text, MemoryAnswerType.FOOD)

            with self.subTest(text=text):
                self.assertEqual(
                    ("FOOD", food),
                    (clues[0]["answerType"], clues[0]["answerValue"]),
                )
                self.assertNotIn(
                    "PERSON",
                    {clue["answerType"] for clue in clues},
                )

    def test_negative_variants_never_become_memory_clues(self):
        texts = (
            "딸이랑 안 갔어",
            "공원에 못 갔어",
            "김밥을 먹지 않았어",
            "운동을 안 했어",
            "방송을 못 봤어",
        )

        for text in texts:
            with self.subTest(text=text):
                self.assertEqual((), extract_memory_clues(text))


if __name__ == "__main__":
    unittest.main()
