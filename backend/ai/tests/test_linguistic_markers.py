import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT / "analysis" / "src"))

import text_linguistic_markers as markers


class LinguisticMarkersTest(unittest.TestCase):
    def test_pronoun_noun_ratio_counts_pronouns_over_nouns(self):
        texts = ["아들이 왔어", "그게 좋았어"]

        # "아들"(NNG) 1개, "그게"의 "그"(NP) 1개 -> 1/1
        self.assertAlmostEqual(1.0, markers.pronoun_noun_ratio(texts))

    def test_pronoun_noun_ratio_is_none_without_nouns(self):
        self.assertIsNone(markers.pronoun_noun_ratio(["그거 좋았어"]))

    def test_pronoun_noun_ratio_is_zero_when_only_nouns_present(self):
        self.assertEqual(0.0, markers.pronoun_noun_ratio(["아들이랑 딸이 왔어"]))

    def test_noun_ratio_is_between_zero_and_one(self):
        ratio = markers.noun_ratio(["오늘 점심에 김치찌개를 먹었어"])

        self.assertIsNotNone(ratio)
        self.assertGreaterEqual(ratio, 0.0)
        self.assertLessEqual(ratio, 1.0)

    def test_noun_ratio_is_none_for_empty_input(self):
        self.assertIsNone(markers.noun_ratio([""]))

    def test_lexical_diversity_is_one_when_all_words_are_unique(self):
        # 짧은 문장(윈도 크기 미만)이라 단순 TTR로 계산됨
        diversity = markers.lexical_diversity_mattr(
            ["오늘 병원에 갔어"], window_size=10
        )

        self.assertEqual(1.0, diversity)

    def test_lexical_diversity_drops_with_repeated_words(self):
        varied = markers.lexical_diversity_mattr(
            ["오늘 병원 마트 공원 학교 회사"], window_size=3
        )
        repetitive = markers.lexical_diversity_mattr(
            ["병원 병원 병원 병원 병원 병원"], window_size=3
        )

        self.assertGreater(varied, repetitive)

    def test_lexical_diversity_is_none_for_empty_input(self):
        self.assertIsNone(markers.lexical_diversity_mattr([""]))

    def test_repetition_score_is_high_for_near_identical_utterances(self):
        texts = ["오늘 점심에 김치찌개를 먹었어", "오늘 점심에 김치찌개를 또 먹었어"]

        self.assertGreater(markers.repetition_score(texts), 0.8)

    def test_repetition_score_is_low_for_unrelated_utterances(self):
        texts = ["오늘 점심에 김치찌개를 먹었어", "어제 공원에서 산책했어"]

        self.assertLess(markers.repetition_score(texts), 0.5)

    def test_repetition_score_is_none_with_fewer_than_two_utterances(self):
        self.assertIsNone(markers.repetition_score(["혼자 있는 발화"]))
        self.assertIsNone(markers.repetition_score([]))


if __name__ == "__main__":
    unittest.main()
