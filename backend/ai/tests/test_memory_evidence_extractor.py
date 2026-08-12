import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


from recall.memory_evidence_extractor import (  # noqa: E402
    extract_answer_value,
    extract_memory_clues,
)
from recall.memory_event import MemoryAnswerType  # noqa: E402


class MemoryEvidenceExtractorTest(unittest.TestCase):
    def test_extracts_typed_values_from_korean_particles(self):
        cases = (
            ("경산 반석 풋살장에서 했어", MemoryAnswerType.PLACE, "경산 반석 풋살장"),
            ("라면과 계란밥을 먹었어", MemoryAnswerType.FOOD, "라면과 계란밥"),
            ("딸이랑 같이 먹었어", MemoryAnswerType.PERSON, "딸"),
            ("오후 세 시에 병원에 갔어", MemoryAnswerType.TIME, "오후 세 시"),
            ("미스터트롯을 봤어", MemoryAnswerType.MEDIA, "미스터트롯"),
            ("리모컨을 소파에 뒀어", MemoryAnswerType.OBJECT, "리모컨"),
            ("공원에서 산책했어", MemoryAnswerType.ACTIVITY, "산책"),
        )

        for text, answer_type, expected in cases:
            with self.subTest(text=text, answer_type=answer_type):
                self.assertEqual(expected, extract_answer_value(text, answer_type))

    def test_returns_empty_value_when_type_is_unknown(self):
        self.assertEqual(
            "",
            extract_answer_value("그냥 그랬어", MemoryAnswerType.UNKNOWN),
        )

    def test_does_not_promote_vague_or_negative_answers(self):
        cases = (
            ("아직은 없어", MemoryAnswerType.FOOD),
            ("뭘 먹었는지 모르겠어", MemoryAnswerType.FOOD),
            ("거기 갔어", MemoryAnswerType.PLACE),
            ("언제인지 모르겠어", MemoryAnswerType.TIME),
            ("아직 안 봤어", MemoryAnswerType.MEDIA),
            ("아무것도 안 했어", MemoryAnswerType.ACTIVITY),
            ("그건 없어", MemoryAnswerType.OBJECT),
        )

        for text, answer_type in cases:
            with self.subTest(text=text, answer_type=answer_type):
                self.assertEqual("", extract_answer_value(text, answer_type))

    def test_extracts_particle_omitted_spoken_food(self):
        self.assertEqual(
            "김치볶음밥",
            extract_answer_value("김치볶음밥 먹었어", MemoryAnswerType.FOOD),
        )

    def test_preserves_solo_as_a_person_answer(self):
        self.assertEqual(
            "혼자",
            extract_answer_value("혼자 먹었어", MemoryAnswerType.PERSON),
        )

    def test_activity_does_not_capture_a_time_phrase(self):
        self.assertEqual(
            "",
            extract_answer_value("식사 후에는 잤어", MemoryAnswerType.ACTIVITY),
        )

    def test_extracts_multiple_clues_from_one_utterance(self):
        self.assertEqual(
            (
                {"answerType": "PERSON", "answerValue": "딸"},
                {"answerType": "PLACE", "answerValue": "공원"},
                {"answerType": "ACTIVITY", "answerValue": "산책"},
            ),
            extract_memory_clues(
                "딸과 공원에서 산책했어",
                MemoryAnswerType.ACTIVITY,
            ),
        )

    def test_food_conjunction_is_not_mistaken_for_person(self):
        self.assertEqual(
            (
                {
                    "answerType": "FOOD",
                    "answerValue": "엽기 떡볶이와 치킨",
                },
            ),
            extract_memory_clues(
                "엽기 떡볶이와 치킨을 먹었어",
                MemoryAnswerType.FOOD,
            ),
        )

        self.assertNotIn(
            "PERSON",
            {
                clue["answerType"]
                for clue in extract_memory_clues(
                    "떡볶이와 치킨을 먹었어",
                    MemoryAnswerType.PERSON,
                )
            },
        )

    def test_time_and_place_are_kept_as_separate_clues(self):
        self.assertEqual(
            (
                {"answerType": "TIME", "answerValue": "오후 세 시"},
                {"answerType": "PLACE", "answerValue": "병원"},
            ),
            extract_memory_clues("오후 세 시에 병원에 갔어"),
        )

    def test_negative_statement_does_not_create_clue(self):
        self.assertEqual(
            (),
            extract_memory_clues(
                "딸이랑 안 갔어",
                MemoryAnswerType.PERSON,
            ),
        )

    def test_correction_keeps_only_latest_statement(self):
        self.assertEqual(
            (
                {"answerType": "PERSON", "answerValue": "아들"},
            ),
            extract_memory_clues(
                "딸이랑 갔어, 아니 아들이랑 갔어",
                MemoryAnswerType.PERSON,
            ),
        )


if __name__ == "__main__":
    unittest.main()
