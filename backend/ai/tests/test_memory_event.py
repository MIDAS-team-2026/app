import sys
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


from recall.memory_event import (  # noqa: E402
    MemoryAnswerType,
    MemoryCandidateDraft,
    MemoryEvidence,
    group_memory_evidence,
    select_memory_candidate_drafts,
    should_continue_memory_event,
)


class MemoryEvidenceGroupingTest(unittest.TestCase):
    @staticmethod
    def _draft(
        record_id,
        text,
        topic,
        answer_type=MemoryAnswerType.UNKNOWN,
        answer_value="",
        quality_score=60,
    ):
        evidence = MemoryEvidence.create(
            source_record_id=record_id,
            text=text,
            topic=topic,
            answer_type=answer_type,
            answer_value=answer_value,
            quality_score=quality_score,
        )
        return MemoryCandidateDraft(topic=evidence.topic, evidence=(evidence,))

    def test_consecutive_details_from_one_topic_become_one_candidate(self):
        evidence = [
            MemoryEvidence.create(
                source_record_id=10,
                text="김치볶음밥 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.FOOD,
                answer_value="김치볶음밥",
                quality_score=70,
            ),
            MemoryEvidence.create(
                source_record_id=11,
                text="딸이랑 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.PERSON,
                answer_value="딸",
                quality_score=60,
                continues_previous_event=True,
            ),
            MemoryEvidence.create(
                source_record_id=12,
                text="집에서 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.PLACE,
                answer_value="집",
                quality_score=60,
                continues_previous_event=True,
            ),
        ]

        drafts = group_memory_evidence(evidence)

        self.assertEqual(1, len(drafts))
        self.assertEqual((10, 11, 12), drafts[0].source_record_ids)
        self.assertEqual(
            {
                "FOOD": ("김치볶음밥",),
                "PERSON": ("딸",),
                "PLACE": ("집",),
            },
            drafts[0].answer_values,
        )
        self.assertEqual(80, drafts[0].quality_score)
        self.assertEqual(10, drafts[0].recall_target.source_record_id)
        self.assertEqual("김치볶음밥 먹었어", drafts[0].recall_target.text)
        self.assertEqual("김치볶음밥", drafts[0].recall_target.answer_value)

    def test_conflicting_values_in_same_topic_remain_separate(self):
        evidence = [
            MemoryEvidence.create(
                source_record_id=20,
                text="아침에 김밥을 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.FOOD,
                answer_value="김밥",
                quality_score=70,
            ),
            MemoryEvidence.create(
                source_record_id=21,
                text="점심에는 국수를 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.FOOD,
                answer_value="국수",
                quality_score=75,
            ),
        ]

        drafts = group_memory_evidence(evidence)

        self.assertEqual(2, len(drafts))
        self.assertEqual((20,), drafts[0].source_record_ids)
        self.assertEqual((21,), drafts[1].source_record_ids)

    def test_same_untyped_detail_kind_is_not_merged_twice(self):
        evidence = [
            MemoryEvidence.create(
                source_record_id=22,
                text="아침에 김밥을 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.FOOD,
                quality_score=70,
            ),
            MemoryEvidence.create(
                source_record_id=23,
                text="점심에는 국수를 먹었어",
                topic="MEAL",
                answer_type=MemoryAnswerType.FOOD,
                quality_score=75,
                continues_previous_event=True,
            ),
        ]

        drafts = group_memory_evidence(evidence)

        self.assertEqual(2, len(drafts))

    def test_payload_contract_accepts_hyeongseop_analysis_fields(self):
        evidence = MemoryEvidence.from_payload(
            {
                "sourceRecordId": 30,
                "transcriptText": "딸과 공원에서 산책했어",
                "topic": "activity",
                "answerType": "person",
                "answerKeyword": "딸",
                "qualityScore": 85,
            }
        )

        self.assertEqual(30, evidence.source_record_id)
        self.assertEqual("ACTIVITY", evidence.topic)
        self.assertEqual(MemoryAnswerType.PERSON, evidence.answer_type)
        self.assertEqual("딸", evidence.answer_value)

    def test_invalid_source_is_rejected(self):
        with self.assertRaises(ValueError):
            MemoryEvidence.from_payload(
                {
                    "sourceRecordId": 0,
                    "sourceText": "공원에 다녀왔어",
                    "topic": "PLACE",
                }
            )

    def test_general_used_and_low_quality_candidates_are_excluded(self):
        drafts = [
            self._draft(40, "아들과 통화했어", "PERSON", quality_score=80),
            self._draft(41, "그냥 있었어", "GENERAL", quality_score=90),
            self._draft(42, "물을 마셨어", "MEAL", quality_score=20),
        ]

        selected = select_memory_candidate_drafts(
            drafts,
            used_source_record_ids={40},
        )

        self.assertEqual([], selected)

    def test_selection_prefers_quality_and_keeps_conversation_order(self):
        drafts = [
            self._draft(50, "공원에서 산책했어", "ACTIVITY", quality_score=65),
            self._draft(51, "김치볶음밥을 먹었어", "MEAL", quality_score=90),
            self._draft(52, "뉴스를 봤어", "MEDIA", quality_score=70),
        ]

        selected = select_memory_candidate_drafts(drafts, max_candidates=2)

        self.assertEqual([51, 52], [item.first_source_record_id for item in selected])

    def test_recall_target_uses_one_original_evidence(self):
        evidence = [
            MemoryEvidence.create(
                source_record_id=60,
                text="공원에 다녀왔어",
                topic="ACTIVITY",
                answer_type=MemoryAnswerType.PLACE,
                answer_value="공원",
                quality_score=65,
            ),
            MemoryEvidence.create(
                source_record_id=61,
                text="딸과 같이 갔어",
                topic="ACTIVITY",
                answer_type=MemoryAnswerType.PERSON,
                answer_value="딸",
                quality_score=80,
                continues_previous_event=True,
            ),
        ]

        draft = group_memory_evidence(evidence)[0]

        self.assertEqual(61, draft.recall_target.source_record_id)
        self.assertEqual("딸과 같이 갔어", draft.recall_target.text)
        self.assertNotIn("공원에 다녀왔어", draft.recall_target.text)

    def test_detail_answer_can_change_slot_without_starting_new_event(self):
        cases = (
            {
                "last_event_topic": "PLACE",
                "detected_topic": "PERSON",
                "question_topics": {"PLACE", "PERSON"},
                "answer_type": MemoryAnswerType.PERSON,
            },
            {
                "last_event_topic": "PERSON",
                "detected_topic": "TIME",
                "question_topics": {"PERSON"},
                "answer_type": MemoryAnswerType.TIME,
            },
        )

        for inputs in cases:
            with self.subTest(inputs=inputs):
                self.assertTrue(should_continue_memory_event(**inputs))

    def test_unrelated_answer_starts_new_event_even_after_followup(self):
        self.assertFalse(
            should_continue_memory_event(
                last_event_topic="MEAL",
                detected_topic="MEDIA",
                question_topics={"MEAL"},
                answer_type=MemoryAnswerType.FOOD,
            )
        )

    def test_question_without_previous_event_anchor_does_not_merge(self):
        self.assertFalse(
            should_continue_memory_event(
                last_event_topic="PLACE",
                detected_topic="PERSON",
                question_topics={"PERSON"},
                answer_type=MemoryAnswerType.PERSON,
            )
        )

    def test_same_source_record_is_selected_only_once_across_topics(self):
        drafts = [
            self._draft(
                70,
                "딸과 공원에 다녀왔어",
                "PERSON",
                MemoryAnswerType.PERSON,
                "딸",
                85,
            ),
            self._draft(
                70,
                "공원에 딸과 다녀왔어",
                "PLACE",
                MemoryAnswerType.PLACE,
                "공원",
                80,
            ),
        ]

        selected = select_memory_candidate_drafts(drafts)

        self.assertEqual(1, len(selected))
        self.assertEqual(70, selected[0].first_source_record_id)

    def test_same_source_text_is_selected_only_once_across_topics(self):
        drafts = [
            self._draft(
                80,
                "딸과 공원에 다녀왔어",
                "PERSON",
                MemoryAnswerType.PERSON,
                "딸",
                85,
            ),
            self._draft(
                81,
                "딸과 공원에 다녀왔어",
                "PLACE",
                MemoryAnswerType.PLACE,
                "공원",
                80,
            ),
        ]

        selected = select_memory_candidate_drafts(drafts)

        self.assertEqual(1, len(selected))
        self.assertEqual(80, selected[0].first_source_record_id)


if __name__ == "__main__":
    unittest.main()
