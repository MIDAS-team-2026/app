import importlib.util
import sys
import types
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


if "requests" not in sys.modules:
    try:
        import requests  # noqa: F401
    except ImportError:
        sys.modules["requests"] = types.ModuleType("requests")

if "openai" not in sys.modules:
    try:
        import openai  # noqa: F401
    except ImportError:
        openai_stub = types.ModuleType("openai")
        openai_stub.OpenAI = object
        sys.modules["openai"] = openai_stub


if "sentence_transformers" not in sys.modules:
    sentence_transformers_stub = types.ModuleType("sentence_transformers")
    sentence_transformers_stub.SentenceTransformer = lambda *_args, **_kwargs: object()
    sentence_transformers_stub.util = SimpleNamespace()
    sys.modules["sentence_transformers"] = sentence_transformers_stub


recall_api_stub = types.ModuleType("recall.recall_api_client")
recall_api_stub.analyze_session_recall = lambda **_kwargs: None
sys.modules.setdefault("recall.recall_api_client", recall_api_stub)


import voice_reply_handler as handler
from recall import conversation_policy
from recall import conversation_recall_generator as recall_generator
from recall import free_talk_question_generator as free_talk_generator
from recall import memory_event
from recall import recall_score_calculator


recall_api_spec = importlib.util.spec_from_file_location(
    "recall_api_client_under_test",
    AI_ROOT / "recall" / "recall_api_client.py",
)
recall_api_client = importlib.util.module_from_spec(recall_api_spec)
recall_api_spec.loader.exec_module(recall_api_client)


class ConversationFlowSimulationTest(unittest.TestCase):
    @staticmethod
    def _recall_client(memory_point, question, answer_keyword="김치볶음밥"):
        response = SimpleNamespace(
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(
                        content=(
                            f"memoryPoint: {memory_point}\n"
                            f"answerKeyword: {answer_keyword}\n"
                            f"question: {question}"
                        )
                    )
                )
            ]
        )
        return SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(create=lambda **_kwargs: response)
            )
        )

    def test_generation_history_does_not_duplicate_latest_answer(self):
        self.assertEqual(
            ["김치볶음밥 먹었어"],
            handler._build_generation_history(
                ["김치볶음밥 먹었어"],
                "김치볶음밥 먹었어",
            ),
        )

    def test_memory_event_answer_type_comes_from_the_question_slot(self):
        cases = (
            ("아까 누구와 통화하셨나요?", memory_event.MemoryAnswerType.PERSON),
            ("아까 어디에 다녀오셨나요?", memory_event.MemoryAnswerType.PLACE),
            ("아까 언제 병원에 다녀오셨나요?", memory_event.MemoryAnswerType.TIME),
            ("아까 어떤 음식을 드셨나요?", memory_event.MemoryAnswerType.FOOD),
        )

        for question, expected in cases:
            with self.subTest(question=question):
                self.assertEqual(
                    expected,
                    memory_event.infer_answer_type(question),
                )

    def test_memory_event_keeps_grounded_source_and_typed_answer(self):
        event = memory_event.MemoryEvent.from_generation(
            source_text="점심에 김치볶음밥을 먹었어",
            memory_point="김치볶음밥을 먹은 일",
            answer_keyword="김치볶음밥",
            question="아까 어떤 음식을 드셨나요?",
            action="EAT",
            quality_score=87,
        ).to_dict()

        self.assertEqual("점심에 김치볶음밥을 먹었어", event["sourceText"])
        self.assertEqual("FOOD", event["answerType"])
        self.assertEqual("MEAL", event["topic"])
        self.assertEqual("김치볶음밥", event["objectName"])
        self.assertIsNone(event["person"])
        self.assertIsNone(event["sourceRecordId"])

    def test_conversation_policy_exposes_the_next_action(self):
        cases = (
            (
                {
                    "candidate_count": 0,
                    "after_recall_answer": False,
                    "should_change_topic": False,
                },
                conversation_policy.ConversationAction.OPEN_TOPIC,
            ),
            (
                {
                    "candidate_count": 1,
                    "after_recall_answer": False,
                    "should_change_topic": False,
                    "needs_memory_detail": False,
                },
                conversation_policy.ConversationAction.FOLLOW_UP,
            ),
            (
                {
                    "candidate_count": 0,
                    "after_recall_answer": False,
                    "should_change_topic": False,
                    "has_followup_context": True,
                },
                conversation_policy.ConversationAction.FOLLOW_UP,
            ),
            (
                {
                    "candidate_count": 1,
                    "after_recall_answer": False,
                    "should_change_topic": True,
                },
                conversation_policy.ConversationAction.CHANGE_TOPIC,
            ),
            (
                {
                    "candidate_count": 0,
                    "after_recall_answer": True,
                    "should_change_topic": False,
                },
                conversation_policy.ConversationAction.RESUME_AFTER_RECALL,
            ),
        )

        for inputs, expected in cases:
            with self.subTest(inputs=inputs):
                decision = conversation_policy.decide_conversation_action(
                    **inputs
                )
                self.assertEqual(expected, decision.action)

    def test_conversation_stage_uses_memory_quality_instead_of_turn_count(self):
        detailed = conversation_policy.decide_conversation_action(
            candidate_count=10,
            after_recall_answer=False,
            should_change_topic=False,
            needs_memory_detail=False,
        )
        incomplete = conversation_policy.decide_conversation_action(
            candidate_count=1,
            after_recall_answer=False,
            should_change_topic=False,
            needs_memory_detail=True,
        )

        self.assertEqual("DEEPEN", detailed.stage)
        self.assertEqual("ANCHOR", incomplete.stage)

    def test_non_memory_topic_can_continue_without_becoming_recall_candidate(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "글쎄",
                "aiReplyText": "요즘 제일 보고 싶은 사람은 누구세요?",
                "answerRole": None,
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "동생",
                "aiReplyText": "",
                "answerRole": None,
            },
        ]

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ):
            question = handler._get_next_normal_question(
                candidate_count=0,
                session_records=records,
                latest_text="동생",
            )

        self.assertFalse(
            recall_generator.score_recall_memory_candidate("동생")["isValid"]
        )
        self.assertIn("그분", question)
        self.assertNotIn("음식", question)

    def test_wish_continues_as_conversation_but_not_as_recall_memory(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "글쎄",
                "aiReplyText": "지금 드시고 싶은 음식이 있으세요?",
                "answerRole": None,
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "피자가 먹고 싶어",
                "aiReplyText": "",
                "answerRole": None,
            },
        ]

        self.assertFalse(
            recall_generator.score_recall_memory_candidate(
                "피자가 먹고 싶어"
            )["isValid"]
        )

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ):
            question = handler._get_next_normal_question(
                candidate_count=0,
                session_records=records,
                latest_text="피자가 먹고 싶어",
            )

        self.assertTrue(
            any(
                phrase in question
                for phrase in ("이유", "드신다면", "누구와")
            )
        )

    def test_generation_history_appends_new_latest_answer(self):
        self.assertEqual(
            ["아들이 보고 싶어", "어제 통화했어"],
            handler._build_generation_history(
                ["아들이 보고 싶어"],
                "어제 통화했어",
            ),
        )

    def test_user_answers_are_not_passed_as_previous_ai_questions(self):
        records = [
            {
                "recordId": index,
                "turnOrder": index,
                "transcriptText": f"고정답변 {index}",
                "aiReplyText": f"고정질문 {index + 1}",
                "answerRole": "FIXED",
            }
            for index in range(1, 6)
        ]
        records.append(
            {
                "recordId": 6,
                "turnOrder": 6,
                "transcriptText": "김치볶음밥 먹었어",
                "aiReplyText": "",
                "answerRole": None,
            }
        )

        captured = {}

        def fake_generate(**kwargs):
            captured.update(kwargs)
            return {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            }

        with patch.object(handler, "generate_safe_followup_question", fake_generate):
            handler._get_next_normal_question(
                candidate_count=1,
                session_records=records,
                latest_text="김치볶음밥 먹었어",
            )

        self.assertNotIn("김치볶음밥 먹었어", captured["previous_questions"])
        self.assertEqual(
            ["김치볶음밥 먹었어"],
            captured["conversation_history"],
        )

    def test_common_answers_map_to_expected_topics(self):
        cases = {
            "김치볶음밥 먹었어": "FOOD",
            "오늘 약 먹었어": "HEALTH",
            "병원에 다녀왔어": "HEALTH",
            "동생이 보고 싶어": "PERSON",
            "아들이랑 통화했어": "PERSON",
            "텔레비전 봤어": "MEDIA",
            "미스터트롯 봤어": "MEDIA",
            "공원에 다녀왔어": "PLACE",
            "집에서 누워 있었어": "REST",
            "오늘 청소했어": "ACTIVITY",
            "마트에서 사과를 샀어": "SHOPPING",
            "오늘 바람이 많이 불었어": "WEATHER",
            "아직은 없어": "LOW_INFO",
            "기억이 안 나": "LOW_INFO",
            "생각이 안 나": "LOW_INFO",
            "오늘 너무 속상했어": "NEGATIVE",
        }

        for answer, expected_topic in cases.items():
            with self.subTest(answer=answer):
                self.assertEqual(expected_topic, handler._detect_topic_in_text(answer))

    def test_short_answer_uses_the_question_it_answers_as_context(self):
        cases = (
            ("요즘 제일 보고 싶은 사람은 누구세요?", "선생님", "PERSON"),
            ("오늘 드신 음식이 있으세요?", "짜장면", "FOOD"),
            ("오늘 어디에 다녀오셨어요?", "복지관", "PLACE"),
        )

        for previous_question, answer, expected_topic in cases:
            with self.subTest(answer=answer):
                self.assertIsNone(handler._detect_topic_in_text(answer))
                self.assertEqual(
                    expected_topic,
                    handler._detect_conversation_topic(
                        answer,
                        [answer],
                        previous_question,
                    ),
                )

        self.assertEqual(
            "MEDIA",
            handler._detect_conversation_topic(
                "집에서 티비 봤어",
                ["집에서 티비 봤어"],
                "요즘 제일 보고 싶은 사람은 누구세요?",
            ),
        )
        self.assertEqual(
            "LOW_INFO",
            handler._detect_conversation_topic(
                "아직은 없어",
                ["아직은 없어"],
                "요즘 제일 보고 싶은 사람은 누구세요?",
            ),
        )

    def test_unknown_person_name_receives_a_person_followup(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "글쎄",
                "aiReplyText": "요즘 제일 보고 싶은 사람은 누구세요?",
                "answerRole": None,
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "선생님",
                "aiReplyText": "",
                "answerRole": None,
            },
        ]

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ):
            question = handler._get_next_normal_question(
                candidate_count=1,
                session_records=records,
                latest_text="선생님",
            )

        self.assertIn(
            question,
            handler._get_topic_aware_fallback_candidates(
                "DEEPEN",
                "선생님",
                ["선생님"],
                "요즘 제일 보고 싶은 사람은 누구세요?",
            ),
        )
        self.assertNotIn("어디에 다녀오", question)

    def test_everyday_object_answer_is_not_treated_as_shopping(self):
        previous_question = "최근에 손에 자주 잡는 물건이 있으세요?"

        self.assertEqual(
            "OBJECT",
            handler._detect_conversation_topic(
                "리모컨",
                ["리모컨"],
                previous_question,
            ),
        )

        object_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "리모컨",
            ["리모컨"],
            previous_question,
        )
        self.assertEqual(
            handler.TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS["OBJECT"]["DEEPEN"],
            object_questions,
        )
        self.assertTrue(
            all("사신" not in question and "고르" not in question for question in object_questions)
        )

        self.assertEqual(
            "SHOPPING",
            handler._detect_conversation_topic(
                "우산",
                ["우산"],
                "마트에서 어떤 물건을 사셨어요?",
            ),
        )

    def test_waited_for_time_answer_keeps_a_daily_routine_context(self):
        previous_question = "요즘 하루 중 기다려지는 시간이 있으세요?"

        self.assertEqual(
            "ROUTINE",
            handler._detect_conversation_topic(
                "저녁",
                ["저녁"],
                previous_question,
            ),
        )
        self.assertEqual(
            handler.TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS["ROUTINE"]["DEEPEN"],
            handler._get_topic_aware_fallback_candidates(
                "DEEPEN",
                "저녁",
                ["저녁"],
                previous_question,
            ),
        )

        self.assertEqual(
            "MEDIA",
            handler._detect_conversation_topic(
                "저녁에는 뉴스를 봐",
                ["저녁에는 뉴스를 봐"],
                previous_question,
            ),
        )

    def test_every_safe_opening_question_has_a_followup_topic(self):
        inferred_topics = [
            handler._detect_topic_from_question(question)
            for question in handler.SAFE_OPENING_QUESTIONS
        ]

        self.assertTrue(all(topic is not None for topic in inferred_topics))
        self.assertEqual("SCENERY", inferred_topics[5])
        self.assertEqual("HOME", inferred_topics[10])

        self.assertEqual(
            "SCENERY",
            handler._detect_conversation_topic(
                "벚꽃",
                ["벚꽃"],
                handler.SAFE_OPENING_QUESTIONS[5],
            ),
        )

    def test_media_consumption_is_not_mistaken_for_its_location(self):
        for answer in (
            "집에서 텔레비전 봤어",
            "공원에서 노래 들었어",
            "병실에서 뉴스를 시청했어",
        ):
            with self.subTest(answer=answer):
                self.assertEqual("MEDIA", handler._detect_topic_in_text(answer))

        self.assertEqual("PLACE", handler._detect_topic_in_text("방송국에 갔어"))

    def test_generic_tv_answer_does_not_assume_a_person_or_song(self):
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "집에서 티비 봤어",
            ["집에서 티비 봤어"],
        )

        self.assertIn("어떤 방송이나 프로그램을 보셨어요?", questions)
        self.assertTrue(all("사람" not in question for question in questions))
        self.assertTrue(all("노래" not in question for question in questions))

        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "집에서 티비 봤어",
                "aiReplyText": "",
                "answerRole": None,
            }
        ]

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ):
            first_followup = handler._get_next_normal_question(
                candidate_count=1,
                session_records=records,
                latest_text="집에서 티비 봤어",
            )

        self.assertEqual("어떤 방송이나 프로그램을 보셨어요?", first_followup)

        drama_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "드라마를 봤어",
            ["드라마를 봤어"],
        )
        self.assertTrue(any("사람" in question for question in drama_questions))
        self.assertTrue(
            all("어떤 방송이나 프로그램" not in question for question in drama_questions)
        )

    def test_staying_home_is_not_treated_as_a_visited_place(self):
        for answer in (
            "집에 있었어",
            "오늘은 집에만 있었어",
            "밖에 안 나가고 집에서 지냈어",
        ):
            with self.subTest(answer=answer):
                self.assertEqual("HOME", handler._detect_topic_in_text(answer))
                questions = handler._get_topic_aware_fallback_candidates(
                    "DEEPEN",
                    answer,
                    [answer],
                )
                self.assertTrue(questions)
                self.assertTrue(all("그곳" not in question for question in questions))

        self.assertEqual("MEDIA", handler._detect_topic_in_text("집에서 티비 봤어"))
        self.assertEqual("REST", handler._detect_topic_in_text("집에서 낮잠 잤어"))
        self.assertEqual("FOOD", handler._detect_topic_in_text("집에서 밥 먹었어"))
        self.assertEqual("PLACE", handler._detect_topic_in_text("집 밖에 다녀왔어"))

    def test_unspecified_place_is_requested_before_place_details(self):
        for answer in (
            "집 밖에 다녀왔어",
            "밖에 나갔다 왔어",
            "어디 좀 다녀왔어",
        ):
            with self.subTest(answer=answer):
                questions = handler._get_topic_aware_fallback_candidates(
                    "DEEPEN",
                    answer,
                    [answer],
                )
                self.assertEqual(["어디에 다녀오셨어요?"], questions)

        specific_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "마트에 다녀왔어",
            ["마트에 다녀왔어"],
        )
        self.assertTrue(specific_questions)
        self.assertTrue(all("그곳" in question for question in specific_questions))

    def test_body_words_without_symptoms_do_not_force_health_topic(self):
        self.assertEqual("FOOD", handler._detect_topic_in_text("사과랑 배를 먹었어"))
        self.assertNotEqual("HEALTH", handler._detect_topic_in_text("머리를 잘랐어"))
        self.assertEqual(
            "PLACE",
            handler._detect_topic_in_text("어깨에 가방을 메고 시장에 갔어"),
        )

    def test_body_words_with_symptoms_keep_health_topic(self):
        for answer in (
            "무릎이 아파서 병원에 갔어",
            "허리가 불편해",
            "어깨가 쑤셔",
            "배가 아파서 약을 먹었어",
        ):
            with self.subTest(answer=answer):
                self.assertEqual("HEALTH", handler._detect_topic_in_text(answer))

    def test_reservation_is_not_mistaken_for_medicine(self):
        self.assertNotEqual("HEALTH", handler._detect_topic_in_text("미용실 예약했어"))
        self.assertNotEqual("HEALTH", handler._detect_topic_in_text("식당 예약했어"))
        self.assertEqual("HEALTH", handler._detect_topic_in_text("병원 예약했어"))
        self.assertEqual("HEALTH", handler._detect_topic_in_text("아침에 약을 먹었어"))

    def test_words_starting_with_medicine_syllable_do_not_force_health_topic(self):
        for answer in (
            "약과를 먹었어",
            "약밥을 먹었어",
            "약수를 마셨어",
        ):
            with self.subTest(answer=answer):
                self.assertEqual("FOOD", handler._detect_topic_in_text(answer))

        self.assertNotEqual("HEALTH", handler._detect_topic_in_text("약간 피곤했어"))
        self.assertTrue(handler._is_negative_response("약간 피곤했어"))
        self.assertTrue(handler._has_medicine_reference("아침에 약을 먹었어"))
        self.assertFalse(handler._has_medicine_reference("약국에 다녀왔어"))

    def test_tired_rest_answer_keeps_rest_topic_with_empathy(self):
        answer = "피곤해서 낮잠 잤어"
        self.assertEqual("REST", handler._detect_topic_in_text(answer))
        self.assertTrue(handler._is_negative_response(answer))

        question = free_talk_generator.build_fallback_with_empathy(
            handler._get_topic_aware_fallback_candidates(
                "DEEPEN",
                answer,
                [answer],
            )[0],
            [answer],
        )
        self.assertTrue(question.startswith("그러셨군요."))
        self.assertNotIn("기분이 조금 가라앉", question)

    def test_appetite_absence_keeps_food_topic_with_empathy(self):
        for answer in (
            "입맛이 없어",
            "밥맛이 없어",
            "식욕이 없어",
            "입맛 없어",
            "밥맛 없어",
            "식욕 없어",
        ):
            with self.subTest(answer=answer):
                self.assertFalse(handler._is_low_info_response(answer))
                self.assertTrue(handler._is_negative_response(answer))
                self.assertEqual("FOOD", handler._detect_topic_in_text(answer))

                questions = handler._get_topic_aware_fallback_candidates(
                    "DEEPEN",
                    answer,
                    [answer],
                )
                self.assertEqual(handler.FOOD_APPETITE_QUESTIONS["DEEPEN"], questions)

                reply = free_talk_generator.build_fallback_with_empathy(
                    questions[0],
                    [answer],
                )
                self.assertTrue(reply.startswith("그러셨군요."))

    def test_short_negative_evaluation_keeps_previous_topic(self):
        food_history = ["김치볶음밥 먹었어", "별로였어"]
        self.assertEqual(
            "FOOD",
            handler._detect_conversation_topic(food_history[-1], food_history),
        )
        food_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            food_history[-1],
            food_history,
        )
        self.assertTrue(food_questions)
        self.assertTrue(all("맛" not in question for question in food_questions))

        media_history = ["텔레비전 봤어", "별로였어"]
        self.assertEqual(
            "MEDIA",
            handler._detect_conversation_topic(media_history[-1], media_history),
        )
        media_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            media_history[-1],
            media_history,
        )
        self.assertTrue(media_questions)
        self.assertTrue(all("기분" not in question for question in media_questions))

    def test_place_and_rest_followups_do_not_repeat_given_details(self):
        place_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "공원에 혼자 갔어",
            ["공원에 혼자 갔어"],
        )
        self.assertTrue(place_questions)
        self.assertTrue(all("혼자" not in question for question in place_questions))

        rest_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "소파에서 낮잠 잤어",
            ["소파에서 낮잠 잤어"],
        )
        self.assertTrue(rest_questions)
        self.assertTrue(all("어디에서" not in question for question in rest_questions))

    def test_person_followup_does_not_repeat_conversation_content(self):
        history = ["아들과 통화했어", "건강 얘기했어"]
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            history[-1],
            history,
        )
        self.assertTrue(questions)
        self.assertTrue(all("어떤 이야기를" not in question for question in questions))

    def test_pharmacy_does_not_assume_medicine_was_taken(self):
        self.assertEqual("PLACE", handler._detect_topic_in_text("약국에 다녀왔어"))
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "약국에 다녀왔어",
            ["약국에 다녀왔어"],
        )
        self.assertTrue(questions)
        self.assertTrue(all("약은" not in question and "약 드신" not in question for question in questions))

    def test_standalone_soup_and_pharmacy_are_distinguished(self):
        self.assertEqual("FOOD", handler._detect_topic_in_text("국을 먹었어"))
        self.assertEqual("PLACE", handler._detect_topic_in_text("약국에 다녀왔어"))

    def test_future_hospital_plan_uses_future_tense(self):
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "내일 병원에 갈 거야",
            ["내일 병원에 갈 거야"],
        )
        self.assertTrue(questions)
        self.assertTrue(all("예정" in question for question in questions))
        self.assertTrue(all("다녀오셨어" not in question for question in questions))

    def test_future_plans_do_not_receive_past_tense_followups(self):
        cases = (
            "내일 아들이 올 거야",
            "모레 공원에 갈 거야",
            "내일 마트에서 장 볼 거야",
            "저녁에 피자를 먹을 거야",
            "내일 드라마를 볼 거야",
            "내일 청소할 거야",
        )

        for answer in cases:
            with self.subTest(answer=answer):
                topic = handler._detect_conversation_topic(answer, [answer])
                questions = handler._get_topic_aware_fallback_candidates(
                    "DEEPEN",
                    answer,
                    [answer],
                )
                self.assertIn(topic, handler.FUTURE_TOPIC_QUESTIONS)
                self.assertTrue(questions)
                self.assertTrue(all("셨어요" not in question for question in questions))

    def test_hospital_visit_without_symptoms_does_not_assume_pain(self):
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "병원에 다녀왔어",
            ["병원에 다녀왔어"],
        )
        self.assertTrue(questions)
        self.assertTrue(
            all(
                "몸 상태" not in question
                and "어느 쪽" not in question
                and "괜찮으세요" not in question
                for question in questions
            )
        )

    def test_wish_and_low_information_are_not_recall_memories(self):
        for answer in (
            "동생이 보고 싶어",
            "아직은 없어",
            "기억이 안 나",
            "생각이 안 나",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_future_plans_are_not_recall_memories(self):
        for answer in (
            "내일 병원에 갈 거야",
            "다음 주에 아들을 만날 거야",
            "모레 피자를 먹으려고 해",
            "내일 손주랑 피자를 먹고 싶어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_ge_future_endings_are_not_treated_as_completed_events(self):
        cases = (
            ("병원에 다녀올게", "HEALTH"),
            ("아들이랑 통화할게", "PERSON"),
            ("저녁에는 피자를 먹을게", "FOOD"),
            ("드라마를 볼게", "MEDIA"),
        )

        for answer, expected_topic in cases:
            with self.subTest(answer=answer):
                self.assertTrue(handler._is_future_response(answer))
                self.assertEqual(expected_topic, handler._detect_topic_in_text(answer))
                self.assertFalse(
                    recall_generator.score_recall_memory_candidate(answer)["isValid"]
                )
                self.assertFalse(
                    free_talk_generator.is_question_grounded_in_history(
                        "그때 누구와 같이 계셨어요?",
                        [answer],
                    )
                )

                questions = handler._get_topic_aware_fallback_candidates(
                    "DEEPEN",
                    answer,
                    [answer],
                )
                self.assertTrue(questions)
                self.assertTrue(all("셨어요" not in question for question in questions))

        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "병원에는 누구와 같이 다녀오셨어요?",
                ["병원에 다녀올게"],
            )
        )

    def test_intention_endings_are_not_treated_as_completed_events(self):
        cases = (
            "병원에 가려고 해",
            "공원에 다녀오려고",
            "아들이랑 통화하려고 해",
            "피자를 먹으려고 해",
            "드라마를 보려고",
            "공원에 갈래",
            "피자 먹을래",
        )

        for answer in cases:
            with self.subTest(answer=answer):
                self.assertTrue(handler._is_future_response(answer))
                self.assertFalse(
                    recall_generator.score_recall_memory_candidate(answer)["isValid"]
                )

    def test_last_correction_controls_memory_and_followup_tense(self):
        corrected_to_past = "병원에 안 갔어. 아니, 오후에 병원에 다녀왔어"
        corrected_to_negation = "오후에 병원에 다녀왔어. 아니, 병원에 안 갔어"
        corrected_from_future = "피자를 먹을게. 아니, 점심에 김치볶음밥을 먹었어"
        corrected_to_future = "점심에 김치볶음밥을 먹었어. 아니, 저녁에 피자를 먹을게"

        self.assertFalse(handler._is_negated_action_response(corrected_to_past))
        self.assertTrue(
            recall_generator.score_recall_memory_candidate(corrected_to_past)["isValid"]
        )
        self.assertTrue(
            free_talk_generator.is_question_grounded_in_history(
                "병원에는 누구와 같이 다녀오셨어요?",
                [corrected_to_past],
            )
        )

        self.assertTrue(handler._is_negated_action_response(corrected_to_negation))
        self.assertFalse(
            recall_generator.score_recall_memory_candidate(corrected_to_negation)["isValid"]
        )
        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "병원에는 누구와 같이 다녀오셨어요?",
                [corrected_to_negation],
            )
        )

        self.assertFalse(handler._is_future_response(corrected_from_future))
        self.assertTrue(
            recall_generator.score_recall_memory_candidate(corrected_from_future)["isValid"]
        )
        self.assertTrue(handler._is_future_response(corrected_to_future))
        self.assertFalse(
            recall_generator.score_recall_memory_candidate(corrected_to_future)["isValid"]
        )

    def test_recall_generation_uses_only_the_final_correction_as_grounding(self):
        history = [
            "점심에 피자를 먹었어. 아니, 라면을 먹었어",
            "오후에 동생과 통화했어",
        ]
        wrong_client = self._recall_client(
            "점심에 피자를 먹은 일",
            "아까 드신 음식이 무엇이었나요?",
            answer_keyword="피자",
        )

        with patch.object(recall_generator, "_get_client", return_value=wrong_client):
            wrong_result = recall_generator.generate_recall_question_from_conversation(
                history
            )

        self.assertEqual("SKIPPED", wrong_result["status"])
        self.assertIn("확인되지 않는 memoryPoint", wrong_result["reason"])

        correct_client = self._recall_client(
            "점심에 라면을 먹은 일",
            "아까 드신 음식이 무엇이었나요?",
            answer_keyword="라면",
        )

        with patch.object(recall_generator, "_get_client", return_value=correct_client):
            correct_result = recall_generator.generate_recall_question_from_conversation(
                history
            )

        self.assertEqual("CREATED", correct_result["status"])
        self.assertEqual(history[0], correct_result["sourceText"])
        self.assertEqual(["라면"], correct_result["answerKeywords"])

    def test_last_correction_controls_topic_and_empathy(self):
        corrected_to_person = "피자를 먹었어. 아니, 동생과 통화했어"
        corrected_to_food = "동생과 통화했어. 아니, 피자를 먹었어"
        corrected_to_positive = "오늘 힘들었어. 아니, 정말 즐거웠어"
        corrected_to_negative = "오늘 즐거웠어. 아니, 너무 힘들었어"

        self.assertEqual("PERSON", handler._detect_topic_in_text(corrected_to_person))
        self.assertEqual("FOOD", handler._detect_topic_in_text(corrected_to_food))
        self.assertTrue(handler._is_positive_response(corrected_to_positive))
        self.assertFalse(handler._is_negative_response(corrected_to_positive))
        self.assertTrue(handler._is_negative_response(corrected_to_negative))
        self.assertFalse(handler._is_positive_response(corrected_to_negative))

        positive_reply = free_talk_generator.build_fallback_with_empathy(
            "그때 누구와 같이 계셨어요?",
            [corrected_to_positive],
        )
        negative_reply = free_talk_generator.build_fallback_with_empathy(
            "그때 누구와 같이 계셨어요?",
            [corrected_to_negative],
        )

        self.assertTrue(positive_reply.startswith("좋으셨겠어요."))
        self.assertTrue(negative_reply.startswith("그러셨군요."))

    def test_free_talk_grounding_rejects_the_corrected_away_detail(self):
        history = ["점심에 피자를 먹었어. 아니, 라면을 먹었어"]

        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "피자는 어디에서 드셨어요?",
                history,
            )
        )
        self.assertTrue(
            free_talk_generator.is_question_grounded_in_history(
                "라면은 어디에서 드셨어요?",
                history,
            )
        )

    def test_negated_events_are_not_recall_memories(self):
        for answer in (
            "오늘 병원에 안 갔어",
            "아들을 못 만났어",
            "약을 안 먹었어",
            "텔레비전을 안 봤어",
            "아들은 오늘 안 왔어",
            "밖에 못 나갔어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_suffix_negation_is_not_a_recall_memory(self):
        for answer in (
            "병원에 가지 않았어",
            "밥을 먹지 않았어",
            "텔레비전을 보지 않았어",
            "아직 전화하지 않았어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_conditional_wish_is_not_a_recall_memory(self):
        for answer in (
            "아들이 왔으면 좋겠어",
            "내일 날씨가 맑으면 좋겠어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_unperformed_or_uncertain_action_is_not_a_recall_memory(self):
        for answer in (
            "약 먹는 걸 깜빡했어",
            "병원에 갈 뻔했어",
            "어제 아들을 만나고 싶었어",
            "텔레비전에서 뉴스를 봤는지 모르겠어",
            "아마 병원에 다녀왔던 것 같아",
            "밥을 먹을까 생각했어",
            "병원에 가기로 했어",
            "산책하려다가 말았어",
            "약을 먹었어야 했어",
            "병원에 가려다가 집에 있었어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_completed_action_after_wish_remains_a_recall_memory(self):
        for answer in (
            "피자가 먹고 싶어서 결국 피자를 먹었어",
            "산책하고 싶어서 공원에 갔어",
            "아들을 만나고 싶어서 전화했어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertTrue(result["isValid"])

    def test_negated_non_food_event_changes_topic(self):
        for answer, forbidden_words in (
            ("오늘 병원에 안 갔어", ("병원", "약", "몸")),
            ("오늘 병원에 가지 않았어", ("병원", "약", "몸")),
            ("아들을 못 만났어", ("아들", "그분")),
            ("텔레비전을 안 봤어", ("텔레비전", "방송", "프로그램")),
        ):
            records = [
                {
                    "recordId": 20,
                    "turnOrder": 1,
                    "transcriptText": answer,
                    "aiReplyText": "",
                    "answerRole": None,
                }
            ]

            with self.subTest(answer=answer), patch.object(
                handler,
                "generate_safe_followup_question",
                side_effect=AssertionError("negated event must change topic before LLM"),
            ):
                question = handler._get_next_normal_question(
                    candidate_count=1,
                    session_records=records,
                    latest_text=answer,
                )
                self.assertFalse(any(word in question for word in forbidden_words))

    def test_completed_action_after_negated_clause_controls_the_topic(self):
        cases = (
            ("병원은 안 갔고 집에서 쉬었어", "REST"),
            ("약은 못 먹었지만 물은 마셨어", "FOOD"),
            ("아들은 못 만났고 동생과 통화했어", "PERSON"),
            ("텔레비전은 안 봤고 낮잠 잤어", "REST"),
        )

        for answer, expected_topic in cases:
            with self.subTest(answer=answer):
                self.assertTrue(handler._is_negated_action_response(answer))
                self.assertTrue(handler._get_confirmed_text_after_negated_action(answer))
                self.assertEqual(expected_topic, handler._detect_topic_in_text(answer))

    def test_completed_action_after_negation_becomes_the_memory_candidate(self):
        cases = (
            ("병원은 안 갔고 집에서 쉬었어", "집에서 쉬었어"),
            ("약은 못 먹었지만 물은 마셨어", "물은 마셨어"),
            ("아들은 못 만났고 동생과 통화했어", "동생과 통화했어"),
            ("텔레비전은 안 봤고 낮잠 잤어", "낮잠 잤어"),
        )

        for answer, expected_memory in cases:
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                selected = recall_generator.select_recall_memory_candidates([answer])

                self.assertTrue(result["isValid"])
                self.assertEqual([expected_memory], selected)

    def test_negation_without_later_action_stays_out_of_memory_candidates(self):
        for answer in (
            "오늘 병원에 안 갔어",
            "약을 못 먹었어",
            "아들을 만나지 않았어",
        ):
            with self.subTest(answer=answer):
                self.assertEqual(
                    [],
                    recall_generator.select_recall_memory_candidates([answer]),
                )

    def test_followup_uses_completed_clause_instead_of_negated_clause(self):
        answer = "약은 못 먹었지만 물은 마셨어"
        records = [
            {
                "recordId": 20,
                "turnOrder": 1,
                "transcriptText": answer,
                "aiReplyText": "",
                "answerRole": None,
            }
        ]

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ) as generator:
            question = handler._get_next_normal_question(
                candidate_count=1,
                session_records=records,
                latest_text=answer,
            )

        self.assertNotIn("식사는 못", question)
        self.assertNotIn("음식", question)
        self.assertIn("마셨어요", question)
        self.assertEqual(["물은 마셨어"], generator.call_args.kwargs["conversation_history"])

    def test_contact_answer_uses_contact_specific_followup(self):
        answer = "아들은 못 만났고 동생과 통화했어"
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            handler._get_confirmed_text_after_negated_action(answer),
            [handler._get_confirmed_text_after_negated_action(answer)],
        )

        self.assertEqual(handler.PERSON_CONTACT_QUESTIONS["DEEPEN"], questions)
        self.assertTrue(all("통화" in question or "이야기" in question for question in questions))

        detailed_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "오후에 집에서 동생과 통화했어",
            ["오후에 집에서 동생과 통화했어"],
        )
        self.assertTrue(detailed_questions)
        self.assertTrue(all("언제" not in question for question in detailed_questions))
        self.assertTrue(all("어디" not in question for question in detailed_questions))

    def test_rich_answer_changes_topic_instead_of_reasking_known_details(self):
        answer = "집에서 피자를 혼자 맛있게 먹었어"
        records = [
            {
                "recordId": 30,
                "turnOrder": 1,
                "transcriptText": answer,
                "aiReplyText": "",
                "answerRole": None,
            }
        ]

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=AssertionError("a completed topic should open a new subject"),
        ):
            question = handler._get_next_normal_question(
                candidate_count=1,
                session_records=records,
                latest_text=answer,
            )

        self.assertNotIn("혼자", question)
        self.assertNotIn("어디에서", question)
        self.assertNotIn("맛", question)
        self.assertNotIn("음식", question)
        self.assertNotIn("드신 것", question)
        self.assertTrue(question.startswith("좋으셨겠어요."))

    def test_conditional_wish_uses_future_person_questions(self):
        answer = "아들이 왔으면 좋겠어"
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            answer,
            [answer],
        )
        self.assertTrue(handler._is_future_response(answer))
        self.assertEqual(handler.FUTURE_TOPIC_QUESTIONS["PERSON"], questions)

    def test_words_starting_with_an_are_not_mistaken_for_negation(self):
        for answer in ("가족이 무사해서 안심했어", "오늘 하루는 안전했어"):
            with self.subTest(answer=answer):
                self.assertFalse(handler._is_negated_action_response(answer))
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertNotIn("incomplete_or_negated_action", result["reasons"])

    def test_one_hundred_sixty_fallback_conversation_combinations_are_safe(self):
        answers = [
            "오늘 김치볶음밥을 먹었어",
            "혼자 밥을 먹었어",
            "집에서 피자를 먹었어",
            "찌개가 맛있었어",
            "피자가 먹고 싶어",
            "오늘 밥을 안 먹었어",
            "아침에 약을 먹었어",
            "오후에 병원에 다녀왔어",
            "오늘 무릎이 아팠어",
            "아들이 보고 싶어",
            "동생이랑 통화했어",
            "공원에 다녀왔어",
            "텔레비전에서 뉴스를 봤어",
            "임영웅 노래를 들었어",
            "오전에 청소했어",
            "소파에서 낮잠을 잤어",
            "마트에서 사과를 샀어",
            "오늘 날씨가 많이 더웠어",
            "혼자 있어서 조금 외로웠어",
            "아직은 없어",
            "기억이 안 나",
            "화분에 물을 줬어",
            "손녀에게 편지를 썼어",
            "내일 병원에 갈 거야",
            "텔레비전을 안 봤어",
            "사과랑 배를 먹었어",
            "머리를 잘랐어",
            "어깨에 가방을 메고 시장에 갔어",
            "미용실 예약했어",
            "약과를 먹었어",
            "약국에 다녀왔어",
            "오늘 너무 피곤했어",
            "텔레비전이 재미없었어",
            "내일 아들이 올 거야",
            "모레 공원에 갈 거야",
            "내일 마트에서 장 볼 거야",
            "저녁에 피자를 먹을 거야",
            "내일 드라마를 볼 거야",
            "병원에 가지 않았어",
            "아들이 왔으면 좋겠어",
        ]
        checked = 0

        def use_fallback(**kwargs):
            return {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            }

        with patch.object(handler, "generate_safe_followup_question", side_effect=use_fallback):
            for answer in answers:
                for candidate_count in range(1, 5):
                    records = [
                        {
                            "recordId": candidate_count,
                            "turnOrder": candidate_count,
                            "transcriptText": answer,
                            "aiReplyText": "",
                            "answerRole": None,
                        }
                    ]
                    question = handler._get_next_normal_question(
                        candidate_count=candidate_count,
                        session_records=records,
                        latest_text=answer,
                    )

                    with self.subTest(answer=answer, candidate_count=candidate_count):
                        self.assertTrue(question)
                        self.assertTrue(question.endswith("?"))
                        self.assertEqual(1, question.count("?"))
                        self.assertTrue(free_talk_generator.is_safe_followup_question(question))
                    checked += 1

        self.assertEqual(160, checked)

    def test_concrete_past_events_are_recall_memories(self):
        for answer in (
            "오늘 김치볶음밥을 먹었어",
            "오후에 동생이랑 통화했어",
            "아침에 병원에 다녀왔어",
            "저녁에 미스터트롯을 봤어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertTrue(result["isValid"])

    def test_unlisted_but_concrete_past_events_are_recall_memories(self):
        for answer in (
            "화분에 물을 줬어",
            "친구와 바둑을 뒀어",
            "손녀에게 편지를 썼어",
            "오전에 책을 읽었어",
            "창문을 열었어",
            "화분을 옮겼어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertTrue(result["isValid"])
                self.assertEqual(
                    [answer],
                    recall_generator.select_recall_memory_candidates([answer]),
                )

    def test_generic_object_action_requires_a_completed_event(self):
        for answer in (
            "형편에 문제가 있어",
            "창문을 열어",
            "약을 먹을 거야",
            "병원에 안 갔어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

    def test_different_objects_in_similar_actions_are_not_same_memory(self):
        self.assertFalse(
            recall_generator.is_similar_memory_point(
                "아들이 사과를 먹은 일",
                "아들이 피자를 먹은 일",
            )
        )

    def test_unlisted_memory_rephrasing_is_detected_as_same_memory(self):
        self.assertTrue(
            recall_generator.is_similar_memory_point(
                "화분에 물을 준 일",
                "오전에 화분에 물을 줬어",
            )
        )

    def test_recall_generation_waits_for_two_valid_memories(self):
        with patch.object(
            recall_generator,
            "_get_client",
            side_effect=AssertionError("LLM must not be called"),
        ):
            result = recall_generator.generate_recall_question_from_conversation(
                ["오늘 김치볶음밥을 먹었어", "아직은 없어", "응"],
            )

        self.assertEqual("SKIPPED", result["status"])

    def test_repeated_memory_does_not_count_as_multiple_candidates(self):
        repeated_memories = [
            "오늘 김치볶음밥을 먹었어",
            "오늘 김치볶음밥을 먹었어",
            "오늘 김치볶음밥을 먹은 일이 있었어",
        ]

        selected = recall_generator.select_recall_memory_candidates(
            repeated_memories
        )

        self.assertEqual(1, len(selected))

        with patch.object(
            recall_generator,
            "_get_client",
            side_effect=AssertionError("LLM must not be called"),
        ):
            result = recall_generator.generate_recall_question_from_conversation(
                repeated_memories
            )

        self.assertEqual("SKIPPED", result["status"])

    def test_distinct_memories_remain_separate_candidates(self):
        selected = recall_generator.select_recall_memory_candidates(
            [
                "오늘 김치볶음밥을 먹었어",
                "오후에 동생이랑 통화했어",
                "저녁에 텔레비전 뉴스를 봤어",
            ]
        )

        self.assertEqual(3, len(selected))

    def test_recall_waits_until_two_memories_have_matured(self):
        self.assertEqual(
            [],
            handler._get_recall_ready_history(
                [
                    "오늘 김치볶음밥을 먹었어",
                    "아직은 없어",
                    "오후에 동생이랑 통화했어",
                ]
            ),
        )

        ready_history = handler._get_recall_ready_history(
            [
                "오늘 김치볶음밥을 먹었어",
                "아직은 없어",
                "오후에 동생이랑 통화했어",
                "저녁에 텔레비전 뉴스를 봤어",
            ]
        )

        self.assertEqual(
            [
                "오늘 김치볶음밥을 먹었어",
                "아직은 없어",
                "오후에 동생이랑 통화했어",
            ],
            ready_history,
        )

    def test_latest_memory_is_not_recalled_immediately(self):
        transcripts = [
            "오늘 김치볶음밥을 먹었어",
            "오후에 동생이랑 통화했어",
            "저녁에 텔레비전 뉴스를 봤어",
        ]

        ready_history = handler._get_recall_ready_history(transcripts)

        self.assertEqual(transcripts[:-1], ready_history)
        self.assertNotIn(transcripts[-1], ready_history)

    def test_repeated_memory_does_not_make_recall_ready(self):
        self.assertEqual(
            [],
            handler._get_recall_ready_history(
                [
                    "오늘 김치볶음밥을 먹었어",
                    "오늘 김치볶음밥을 먹은 일이 있었어",
                    "아직은 없어",
                    "응",
                ]
            ),
        )

    def test_recall_cycle_excludes_fixed_initial_and_recall_answers(self):
        records = [
            {
                "recordId": index,
                "transcriptText": f"고정 답변 {index}",
                "answerRole": "FIXED",
            }
            for index in range(1, 6)
        ]
        records.extend(
            [
                {"recordId": 6, "transcriptText": "김치볶음밥 먹었어", "answerRole": "INITIAL"},
                {"recordId": 7, "transcriptText": "김치볶음밥", "answerRole": "RECALL"},
                {"recordId": 8, "transcriptText": "동생이랑 통화했어", "answerRole": None},
            ]
        )

        self.assertEqual(
            ["동생이랑 통화했어"],
            handler._extract_recall_candidate_transcripts(records),
        )

    def test_same_day_new_session_collects_free_talk_without_fixed_records(self):
        records = [
            {
                "recordId": 101,
                "turnOrder": 1,
                "transcriptText": "오늘 김치볶음밥을 먹었어",
                "aiReplyText": "",
                "answerRole": None,
            }
        ]

        self.assertEqual(
            ["오늘 김치볶음밥을 먹었어"],
            handler._extract_recall_candidate_transcripts(records),
        )

    def test_partially_completed_fixed_questions_do_not_enter_free_talk(self):
        records = [
            {
                "recordId": index,
                "transcriptText": f"고정 답변 {index}",
                "answerRole": "FIXED",
            }
            for index in range(1, 4)
        ]
        records.append(
            {
                "recordId": 4,
                "transcriptText": "현재 고정 질문 답변",
                "answerRole": None,
            }
        )

        self.assertEqual([], handler._extract_recall_candidate_transcripts(records))

    def test_same_day_session_processes_first_answer_as_free_talk(self):
        records = [
            {
                "recordId": 101,
                "turnOrder": 1,
                "transcriptText": "오늘 김치볶음밥을 먹었어",
                "aiReplyText": "",
                "answerRole": None,
                "recallQuestionId": None,
            }
        ]
        saved_replies = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(handler, "_is_fixed_questions_done_today", return_value=True),
            patch.object(handler, "_save_ai_reply", side_effect=lambda **kwargs: saved_replies.append(kwargs)),
            patch.object(
                handler,
                "generate_safe_followup_question",
                side_effect=lambda **kwargs: {
                    "nextQuestion": kwargs["fallback_question"],
                    "shouldChangeTopic": False,
                    "reason": "simulation",
                },
            ),
        ):
            handler.process_voice_reply(
                record_id=101,
                session_id=20,
                user_id=2,
                transcript_text="오늘 김치볶음밥을 먹었어",
            )

        self.assertEqual(1, len(saved_replies))
        self.assertNotIn(
            saved_replies[0]["reply_text"],
            handler.SAFE_OPENING_QUESTIONS,
        )

    def test_grounding_rejects_specific_detail_absent_from_history(self):
        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "그때 제주도 여행은 누구와 가셨어요?",
                ["오늘 김치볶음밥을 먹었어"],
            )
        )

    def test_grounding_accepts_generic_one_step_followup(self):
        self.assertTrue(
            free_talk_generator.is_question_grounded_in_history(
                "그때 누구와 같이 계셨어요?",
                ["오늘 김치볶음밥을 먹었어"],
            )
        )

    def test_grounding_accepts_safe_pronoun_followup(self):
        self.assertTrue(
            free_talk_generator.is_question_grounded_in_history(
                "그분과 최근에 어떤 이야기를 나누셨어요?",
                ["오늘 아들이 다녀갔어"],
            )
        )

    def test_person_followup_does_not_assume_recent_contact(self):
        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "그분과 최근에 어떤 이야기를 나누셨어요?",
                ["아들이 보고 싶어"],
            )
        )
        self.assertTrue(
            free_talk_generator.is_question_grounded_in_history(
                "그분과 최근에 어떤 이야기를 나누셨어요?",
                ["어제 아들과 통화했어"],
            )
        )

        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "아들이 보고 싶어",
            ["아들이 보고 싶어"],
        )
        self.assertTrue(questions)
        self.assertTrue(all("이야기를 나누셨어" not in question for question in questions))

    def test_followup_does_not_turn_future_wish_or_negation_into_past_event(self):
        invalid_pairs = (
            ("그 음식은 어디에서 드셨어요?", "피자가 먹고 싶어"),
            ("그 음식은 어디에서 드셨어요?", "저녁에 피자를 먹을 거야"),
            ("그 음식은 누구와 같이 드셨어요?", "오늘 밥을 안 먹었어"),
            ("그곳에는 누구와 같이 가셨어요?", "모레 공원에 갈 거야"),
        )

        for question, answer in invalid_pairs:
            with self.subTest(answer=answer):
                self.assertFalse(
                    free_talk_generator.is_question_grounded_in_history(
                        question,
                        [answer],
                    )
                )

        self.assertTrue(
            free_talk_generator.is_question_grounded_in_history(
                "그 음식은 어디에서 드셨어요?",
                ["김치볶음밥을 먹었어"],
            )
        )

    def test_grounding_rejects_different_person_in_same_topic(self):
        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "그때 손주와 어디로 여행을 가셨어요?",
                ["오늘 아들이 다녀갔어"],
            )
        )

    def test_pronoun_grounding_does_not_allow_hallucinated_event(self):
        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "그분과 제주도 여행은 어땠나요?",
                ["오늘 아들이 다녀갔어"],
            )
        )

    def test_shared_word_does_not_allow_hallucinated_location(self):
        self.assertFalse(
            free_talk_generator.is_question_grounded_in_history(
                "김치볶음밥을 제주도에서 드셨나요?",
                ["오늘 김치볶음밥을 먹었어"],
            )
        )

    def test_different_followup_focus_is_not_treated_as_duplicate(self):
        self.assertFalse(
            free_talk_generator.is_similar_to_previous_question(
                "그 음식은 어디에서 드셨어요?",
                ["그 음식은 누구와 같이 드셨어요?"],
            )
        )
        self.assertFalse(
            free_talk_generator.is_similar_to_previous_question(
                "티비나 방송은 언제쯤 보셨어요?",
                ["그 방송에서 기억나는 내용이 있으세요?"],
            )
        )
        self.assertFalse(
            free_talk_generator.is_similar_to_previous_question(
                "어떤 방송이나 프로그램을 보셨어요?",
                ["그 방송에서 기억나는 내용이 있으세요?"],
            )
        )
        self.assertFalse(
            free_talk_generator.is_similar_to_previous_question(
                "그 시간에는 보통 무엇을 하세요?",
                ["그 시간이 기다려지는 이유가 있으세요?"],
            )
        )
        self.assertFalse(
            free_talk_generator.is_similar_to_previous_question(
                "오늘 드신 것 중에 기억나는 음식이 있으세요?",
                ["오늘 보신 것 중에 기억나는 장면이 있으세요?"],
            )
        )
        self.assertFalse(
            free_talk_generator.is_similar_to_previous_question(
                "어떤 방송이나 프로그램을 보셨어요?",
                ["그 방송에서 가장 먼저 떠오르는 사람이 있으세요?"],
            )
        )

    def test_same_followup_intent_is_treated_as_duplicate(self):
        self.assertTrue(
            free_talk_generator.is_similar_to_previous_question(
                "오늘 드신 음식 중에 기억나는 것이 있으세요?",
                ["오늘 먹은 음식 중 기억나는 게 있으세요?"],
            )
        )

    def test_question_picker_does_not_bypass_similarity_filter(self):
        candidates = [
            "오늘 드신 음식 중에 기억나는 것이 있으세요?",
            "오늘 먹은 음식 중 기억나는 게 있으세요?",
        ]
        selected = handler._pick_non_repeated_question(
            candidates=candidates,
            used_questions=set(),
            previous_questions=["오늘 드신 음식 중 기억나는 게 있으세요?"],
            start_index=0,
        )
        self.assertIsNone(selected)

    def test_string_false_does_not_force_topic_change(self):
        response = SimpleNamespace(
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(
                        content=(
                            '{"nextQuestion":"그때 누구와 같이 계셨어요?",'
                            '"shouldChangeTopic":"false","reason":"이어 묻기"}'
                        )
                    )
                )
            ]
        )
        client = SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(create=lambda **_kwargs: response)
            )
        )

        with patch.object(free_talk_generator, "_get_client", return_value=client):
            result = free_talk_generator.generate_safe_followup_question(
                conversation_history=["오늘 김치볶음밥을 먹었어"],
                stage="DEEPEN",
                fallback_question="그 음식은 어디에서 드셨어요?",
                previous_questions=[],
            )

        self.assertFalse(result["shouldChangeTopic"])

    def test_low_info_transition_does_not_say_it_is_okay(self):
        questions = (
            handler.AFTER_LOW_INFO_RECALL_OPENING_QUESTIONS
            + handler.REPEATED_LOW_INFO_QUESTIONS["DEEPEN"]
            + handler.REPEATED_LOW_INFO_QUESTIONS["ANCHOR"]
        )
        self.assertTrue(all("괜찮습니다" not in question for question in questions))

    def test_recall_and_low_info_transitions_use_concrete_topics(self):
        overly_broad_phrases = (
            "오늘 하루 중 편하게 이야기하고 싶은 일",
            "오늘 하루에서 가장 먼저 떠오르는 일",
            "오늘 하루 중 가장 편했던 순간",
            "오늘 편하게 떠오르는 일",
            "지금 편하게 생각나는 일",
        )
        transition_questions = [
            *handler.AFTER_RECALL_OPENING_QUESTIONS,
            *handler.REPEATED_LOW_INFO_QUESTIONS["DEEPEN"],
            *handler.REPEATED_LOW_INFO_QUESTIONS["ANCHOR"],
            *handler.TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS["LOW_INFO"]["ANCHOR"],
        ]

        for question in transition_questions:
            with self.subTest(question=question):
                self.assertFalse(
                    any(phrase in question for phrase in overly_broad_phrases)
                )

    def test_low_info_count_resets_after_recall_boundary(self):
        records = [
            {
                "recordId": 6,
                "turnOrder": 6,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "아까 드신 음식이 무엇이었나요?",
                "answerRole": "INITIAL",
                "recallQuestionId": 99,
            },
            {
                "recordId": 7,
                "turnOrder": 7,
                "transcriptText": "기억이 안 나",
                "aiReplyText": "오늘 드신 음식 이야기도 해볼까요?",
                "answerRole": "RECALL",
                "recallQuestionId": 99,
            },
            {
                "recordId": 8,
                "turnOrder": 8,
                "transcriptText": "아직은 없어",
                "aiReplyText": "",
                "answerRole": None,
                "recallQuestionId": None,
            },
        ]

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ):
            first_question = handler._get_next_normal_question(
                candidate_count=1,
                session_records=records,
                latest_text="아직은 없어",
            )

        self.assertIn(
            first_question,
            handler.TOPIC_AWARE_STAGE_FALLBACK_QUESTIONS["LOW_INFO"]["DEEPEN"],
        )
        self.assertNotIn(
            first_question,
            handler.REPEATED_LOW_INFO_QUESTIONS["DEEPEN"],
        )

        records.append(
            {
                "recordId": 9,
                "turnOrder": 9,
                "transcriptText": "생각이 안 나",
                "aiReplyText": first_question,
                "answerRole": None,
                "recallQuestionId": None,
            }
        )

        with patch.object(
            handler,
            "generate_safe_followup_question",
            side_effect=lambda **kwargs: {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            },
        ):
            repeated_question = handler._get_next_normal_question(
                candidate_count=2,
                session_records=records,
                latest_text="생각이 안 나",
            )

        self.assertIn(
            repeated_question,
            handler.REPEATED_LOW_INFO_QUESTIONS["DEEPEN"],
        )

    def test_negative_recall_answers_are_detected(self):
        for answer in ("슬펐어", "외로웠어", "무서웠어", "불안했어", "서운했어"):
            with self.subTest(answer=answer):
                self.assertTrue(handler._is_negative_response(answer))

    def test_negative_emotion_has_priority_over_low_information(self):
        answer = "기억이 안 나서 속상해"
        self.assertEqual("NEGATIVE", handler._detect_topic_in_text(answer))
        self.assertIs(
            handler.AFTER_NEGATIVE_RECALL_OPENING_QUESTIONS,
            handler._get_after_recall_opening_candidates(answer),
        )

    def test_common_tired_or_disappointed_answers_receive_empathy(self):
        for answer in (
            "오늘 너무 피곤했어",
            "기운이 없었어",
            "텔레비전이 재미없었어",
            "밥이 맛없었어",
        ):
            with self.subTest(answer=answer):
                self.assertTrue(handler._is_negative_response(answer))

        reply = free_talk_generator.build_fallback_with_empathy(
            "오늘 집에서는 무엇을 하셨어요?",
            ["오늘 너무 피곤했어"],
        )
        self.assertTrue(reply.startswith("그러셨군요."))

    def test_negated_positive_words_do_not_receive_positive_empathy(self):
        for answer in (
            "오늘 기분이 안 좋아",
            "기분이 좋지 않았어",
            "오늘 기분이 나빴어",
            "조금 아쉬웠어",
        ):
            with self.subTest(answer=answer):
                self.assertTrue(handler._is_negative_response(answer))
                self.assertFalse(handler._is_positive_response(answer))
                self.assertEqual("NEGATIVE", handler._detect_topic_in_text(answer))

                reply = free_talk_generator.build_fallback_with_empathy(
                    "오늘 집에서는 무엇을 하셨어요?",
                    [answer],
                )
                self.assertTrue(reply.startswith("그러셨군요."))

        self.assertEqual("HEALTH", handler._detect_topic_in_text("오늘 몸이 안 좋아"))
        self.assertEqual("MEDIA", handler._detect_topic_in_text("방송이 재미가 없었어"))
        self.assertEqual("FOOD", handler._detect_topic_in_text("밥이 맛없었어"))

    def test_negated_negative_words_do_not_receive_negative_empathy(self):
        for answer in (
            "오늘은 힘들지 않았어",
            "기분이 나쁘지 않았어",
            "몸이 불편하지 않았어",
            "별로 걱정되지 않았어",
            "오늘은 외롭지 않았어",
            "많이 피곤하지 않았어",
        ):
            with self.subTest(answer=answer):
                self.assertFalse(handler._is_negative_response(answer))

                reply = free_talk_generator.build_fallback_with_empathy(
                    "오늘 집에서는 무엇을 하셨어요?",
                    [answer],
                )
                self.assertFalse(reply.startswith("그러셨군요."))

        self.assertNotEqual(
            "NEGATIVE",
            handler._detect_topic_in_text("오늘은 힘들지 않았어"),
        )

    def test_real_negative_cue_after_negated_phrase_is_preserved(self):
        answer = "오늘은 힘들지 않았는데 무릎은 아파"

        self.assertTrue(handler._is_negative_response(answer))

        reply = free_talk_generator.build_fallback_with_empathy(
            "그때는 어디에 계셨어요?",
            [answer],
        )
        self.assertTrue(reply.startswith("그러셨군요."))

    def test_prefix_negated_negative_words_do_not_trigger_negative_empathy(self):
        for answer in (
            "오늘은 안 아파",
            "안 힘들어",
            "걱정 안 돼",
            "안 피곤해",
            "기분이 안 나빠",
            "오늘은 아프진 않아",
            "그렇게 힘들지는 않아",
            "지금은 피곤하진 않아",
            "몸이 불편하진 않아",
        ):
            with self.subTest(answer=answer):
                self.assertFalse(handler._is_negative_response(answer))
                reply = free_talk_generator.build_fallback_with_empathy(
                    "그때는 어디에 계셨어요?",
                    [answer],
                )
                self.assertFalse(reply.startswith("그러셨군요."))

    def test_positive_cue_after_negated_positive_phrase_is_preserved(self):
        answer = "좋지는 않았지만 음식은 맛있었어"

        self.assertTrue(handler._is_positive_response(answer))
        reply = free_talk_generator.build_fallback_with_empathy(
            "그때는 어디에 계셨어요?",
            [answer],
        )
        self.assertTrue(reply.startswith("좋으셨겠어요."))

    def test_existing_empathy_prefix_is_not_duplicated(self):
        for question in (
            "그렇군요. 그때 어디에 계셨어요?",
            "알겠습니다. 오늘 무엇을 하셨어요?",
            "그랬군요. 지금은 어떠세요?",
        ):
            with self.subTest(question=question):
                self.assertEqual(
                    question,
                    free_talk_generator.build_fallback_with_empathy(
                        question,
                        ["오늘 힘들었어"],
                    ),
                )

    def test_opposite_empathy_prefix_is_replaced(self):
        self.assertEqual(
            "그러셨군요. 누구와 함께 계셨어요?",
            free_talk_generator.build_fallback_with_empathy(
                "좋았겠어요. 누구와 함께 계셨어요?",
                ["오늘 힘들었어"],
            ),
        )
        self.assertEqual(
            "좋으셨겠어요. 그때 어디에 계셨어요?",
            free_talk_generator.build_fallback_with_empathy(
                "그랬군요. 그때 어디에 계셨어요?",
                ["오늘 즐거웠어"],
            ),
        )

    def test_positive_recall_answer_gets_positive_acknowledgement(self):
        records = [
            {
                "recordId": 10,
                "turnOrder": 10,
                "transcriptText": "아들이 와서 정말 좋았어",
                "answerRole": "RECALL",
            }
        ]
        result = handler._with_recall_transition_acknowledgement(
            "이번에는 오늘 드신 음식 이야기도 해볼까요?",
            records,
        )
        self.assertTrue(result.startswith("좋으셨겠어요."))

    def test_duplicate_memory_point_is_rejected_after_llm_generation(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "조금 전에 드신 음식이 무엇이었나요?",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
                used_memory_points=["점심에 김치볶음밥을 먹었다는 것"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("이미 사용한", result["reason"])

    def test_hallucinated_memory_point_is_rejected(self):
        client = self._recall_client(
            "제주도 공원에 다녀온 일",
            "아까 다녀오신 곳이 어디였나요?",
            answer_keyword="공원",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("확인되지 않는", result["reason"])

    def test_memory_point_cannot_add_person_place_or_time(self):
        cases = (
            ("딸과 통화한 일", "아들과 통화했어"),
            ("공원에서 아들과 산책한 일", "아들과 산책했어"),
            ("어제 아들과 통화한 일", "아들과 통화했어"),
            ("오후 3시에 약을 먹은 일", "약을 먹었어"),
        )

        for memory_point, source_text in cases:
            with self.subTest(memory_point=memory_point):
                self.assertFalse(
                    recall_generator.is_memory_point_grounded(memory_point, source_text)
                )

    def test_memory_point_cannot_change_the_source_action(self):
        cases = (
            ("김치볶음밥을 산 일", "김치볶음밥을 먹었어"),
            ("병원에 다녀온 일", "병원에서 쉬었어"),
            ("아들과 만난 일", "아들과 통화했어"),
        )

        for memory_point, source_text in cases:
            with self.subTest(memory_point=memory_point):
                self.assertFalse(
                    recall_generator.is_memory_point_grounded(memory_point, source_text)
                )

    def test_memory_point_allows_grounded_paraphrase(self):
        self.assertTrue(
            recall_generator.is_memory_point_grounded(
                "점심에 김치볶음밥을 먹은 일",
                "점심에 김치볶음밥을 먹었어",
            )
        )
        self.assertTrue(
            recall_generator.is_memory_point_grounded(
                "아들과 통화한 일",
                "아들이랑 통화했어",
            )
        )
        self.assertTrue(
            recall_generator.is_memory_point_grounded(
                "딸과 통화한 일",
                "딸이랑 통화했어",
            )
        )
        self.assertTrue(
            recall_generator.is_memory_point_grounded(
                "약을 먹은 일",
                "약 먹었어",
            )
        )

    def test_short_keyword_particle_normalization_does_not_split_nouns(self):
        self.assertTrue(recall_generator._keyword_occurs_in_text("딸", "딸과 통화했어"))
        self.assertTrue(recall_generator._keyword_occurs_in_text("약", "약을 먹었어"))
        self.assertFalse(recall_generator._keyword_occurs_in_text("사", "사과를 먹었어"))
        self.assertEqual({"사과"}, recall_generator._content_words("사과를 먹었어"))
        self.assertFalse(recall_generator._keyword_occurs_in_text("약", "약과 먹었어"))
        self.assertFalse(recall_generator._keyword_occurs_in_text("국", "국가 대표를 봤어"))
        self.assertTrue(recall_generator._keyword_occurs_in_text("약과", "약과 먹었어"))

    def test_single_syllable_memory_hints_do_not_match_compound_words(self):
        for answer in (
            "약간 피곤했어",
            "형편이 어려웠어",
        ):
            with self.subTest(answer=answer):
                result = recall_generator.score_recall_memory_candidate(answer)
                self.assertFalse(result["isValid"])

        self.assertFalse(
            recall_generator.is_answer_keyword_compatible_with_question(
                "딸기",
                "누구와 함께 드셨어요?",
                "딸기를 먹었어",
            )
        )
        self.assertTrue(
            recall_generator.is_answer_keyword_compatible_with_question(
                "딸",
                "누구와 통화하셨어요?",
                "딸과 통화했어",
            )
        )

        self.assertTrue(
            recall_generator.score_recall_memory_candidate(
                "피곤해서 낮잠 잤어"
            )["isValid"]
        )

    def test_keyword_fallback_keeps_supported_single_syllable_nouns(self):
        self.assertEqual(
            ["약", "먹"],
            recall_score_calculator.extract_keywords_from_text("약을 먹었어"),
        )
        self.assertIn(
            "차",
            recall_score_calculator.extract_keywords_from_text("차도 마셨어"),
        )
        self.assertNotIn(
            "사",
            recall_score_calculator.extract_keywords_from_text("사과를 먹었어"),
        )

    def test_answer_keyword_must_exist_in_original_source(self):
        self.assertFalse(
            recall_generator.is_answer_keyword_grounded(
                "딸",
                "딸과 통화한 일",
                "아들과 통화했어",
            )
        )
        self.assertFalse(
            recall_generator.is_answer_keyword_grounded(
                "점심",
                "김치볶음밥을 먹은 일",
                "점심에 김치볶음밥을 먹었어",
            )
        )

    def test_answer_keyword_must_match_the_question_slot(self):
        invalid_cases = (
            ("김치볶음밥", "아까 누구와 같이 드셨나요?", "김치볶음밥을 먹었어"),
            ("아들", "아까 어디에 다녀오셨나요?", "아들과 통화했어"),
            ("병원", "아까 언제 다녀오셨나요?", "오후에 병원에 다녀왔어"),
            ("점심", "아까 드신 음식이 무엇이었나요?", "점심에 김치볶음밥을 먹었어"),
        )

        for keyword, question, source_text in invalid_cases:
            with self.subTest(question=question):
                self.assertFalse(
                    recall_generator.is_answer_keyword_compatible_with_question(
                        keyword,
                        question,
                        source_text,
                    )
                )

        valid_cases = (
            ("아들", "아까 누구와 통화하셨나요?", "아들과 통화했어"),
            ("영희", "아까 누구와 통화하셨나요?", "영희와 통화했어"),
            ("경로당", "아까 어디에 다녀오셨나요?", "경로당에 다녀왔어"),
            ("오후", "아까 언제 다녀오셨나요?", "오후에 병원에 다녀왔어"),
        )

        for keyword, question, source_text in valid_cases:
            with self.subTest(question=question, keyword=keyword):
                self.assertTrue(
                    recall_generator.is_answer_keyword_compatible_with_question(
                        keyword,
                        question,
                        source_text,
                    )
                )

    def test_generated_question_with_mismatched_answer_slot_is_rejected(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "아까 누구와 같이 드셨나요?",
            answer_keyword="김치볶음밥",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("답변 종류", result["reason"])

    def test_generated_keyword_missing_from_memory_point_is_rejected(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "아까 드신 음식이 무엇이었나요?",
            answer_keyword="점심",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("확인되지 않습니다", result["reason"])

    def test_semantically_repeated_recall_question_is_rejected(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "조금 전에 드신 음식이 무엇이었나요?",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
                previous_questions=["아까 드신 음식이 무엇이었나요?"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("유사한 질문", result["reason"])

    def test_recall_generation_returns_the_selected_source_text(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "조금 전에 드신 음식이 무엇이었나요?",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("CREATED", result["status"])
        self.assertEqual("점심에 김치볶음밥을 먹었어", result["sourceText"])
        self.assertEqual("FOOD", result["memoryEvent"]["answerType"])
        self.assertEqual("MEAL", result["memoryEvent"]["topic"])
        self.assertEqual(
            "김치볶음밥",
            result["memoryEvent"]["objectName"],
        )
        self.assertEqual(
            result["memoryQualityScore"],
            result["memoryEvent"]["qualityScore"],
        )

    def test_recall_question_that_reveals_answer_is_rejected(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "아까 김치볶음밥을 드셨는데 무엇을 드셨나요?",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("answerKeyword", result["reason"])

    def test_recall_question_can_use_generic_category_without_revealing_answer(self):
        self.assertFalse(
            recall_generator.question_reveals_memory_answer(
                "김치볶음밥",
                "조금 전에 드신 음식이 무엇이었나요?",
            )
        )

    def test_answer_keyword_must_exist_in_memory_source(self):
        client = self._recall_client(
            "김치볶음밥을 먹은 일",
            "조금 전에 드신 음식이 무엇이었나요?",
            answer_keyword="갈비탕",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("확인되지", result["reason"])

    def test_single_syllable_answer_keyword_requires_token_grounding(self):
        self.assertTrue(
            recall_generator.is_answer_keyword_grounded(
                "약",
                "아침에 약을 먹은 일",
                "아침에 약을 먹었어",
            )
        )

    def test_answer_keyword_grounding_accepts_spacing_variation(self):
        self.assertTrue(
            recall_generator.is_answer_keyword_grounded(
                "김치볶음밥",
                "김치 볶음밥을 먹은 일",
                "점심에 김치 볶음밥을 먹었어",
            )
        )
        self.assertTrue(
            recall_generator.question_reveals_memory_answer(
                "미스터 트롯",
                "아까 미스터트롯을 보셨나요?",
            )
        )
        self.assertFalse(
            recall_generator.is_answer_keyword_grounded(
                "약",
                "병원을 예약한 일",
                "병원을 예약했어",
            )
        )
        self.assertFalse(
            recall_generator.question_reveals_memory_answer(
                "약",
                "병원 예약은 언제 하셨어요?",
            )
        )
        self.assertTrue(
            recall_generator.question_reveals_memory_answer(
                "약",
                "아까 약은 언제 드셨어요?",
            )
        )

    def test_answer_keyword_rejects_multiple_answers(self):
        client = self._recall_client(
            "아들과 김치볶음밥을 먹은 일",
            "조금 전에 누구와 식사하셨나요?",
            answer_keyword="아들, 김치볶음밥",
        )

        with patch.object(recall_generator, "_get_client", return_value=client):
            result = recall_generator.generate_recall_question_from_conversation(
                ["점심에 아들과 김치볶음밥을 먹었어", "오후에 병원에 다녀왔어"],
            )

        self.assertEqual("SKIPPED", result["status"])
        self.assertIn("한 개", result["reason"])

    def test_answer_keyword_allows_place_name_ending_in_da(self):
        self.assertTrue(
            recall_generator.is_valid_answer_keyword_format("캐나다")
        )

    def test_recall_output_parser_accepts_markdown_and_label_case(self):
        parsed = recall_generator.parse_recall_generation_content(
            "- **MemoryPoint:** 김치볶음밥을 먹은 일\n"
            "* `AnswerKeyword`: 김치볶음밥\n"
            "> **Question:** 조금 전에 드신 음식이 무엇이었나요?"
        )
        self.assertEqual("김치볶음밥을 먹은 일", parsed["memoryPoint"])
        self.assertEqual("김치볶음밥", parsed["answerKeyword"])
        self.assertEqual(
            "조금 전에 드신 음식이 무엇이었나요?",
            parsed["question"],
        )

    def test_recall_question_format_rejects_statement_and_multiple_questions(self):
        self.assertFalse(
            recall_generator.is_valid_recall_question_format(
                "아까 드신 음식을 말씀해주세요."
            )
        )
        self.assertFalse(
            recall_generator.is_valid_recall_question_format(
                "무엇을 드셨나요? 누구와 드셨나요?"
            )
        )
        self.assertTrue(
            recall_generator.is_valid_recall_question_format(
                "조금 전에 드신 음식이 무엇이었나요?"
            )
        )

    def test_saved_recall_question_updates_target_keyword(self):
        post_response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: {"questionId": 77, "questionText": "무엇을 드셨나요?"},
        )
        put_response = SimpleNamespace(raise_for_status=lambda: None)

        with (
            patch.object(recall_generator.requests, "post", return_value=post_response, create=True),
            patch.object(recall_generator.requests, "put", return_value=put_response, create=True) as put_mock,
        ):
            saved = recall_generator.save_recall_question_to_spring(
                user_id=2,
                memory_point="김치볶음밥을 먹은 일",
                question_text="무엇을 드셨나요?",
                answer_keywords=["김치볶음밥"],
            )

        self.assertEqual(["김치볶음밥"], saved["keywords"])
        self.assertEqual({"keywords": ["김치볶음밥"]}, put_mock.call_args.kwargs["json"])

    def test_keyword_update_failure_keeps_saved_question_usable(self):
        post_response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: {"questionId": 78, "questionText": "무엇을 드셨나요?"},
        )

        with (
            patch.object(recall_generator.requests, "post", return_value=post_response, create=True),
            patch.object(
                recall_generator.requests,
                "put",
                side_effect=TimeoutError("simulated keyword timeout"),
                create=True,
            ) as put_mock,
        ):
            saved = recall_generator.save_recall_question_to_spring(
                user_id=2,
                memory_point="김치볶음밥을 먹은 일",
                question_text="무엇을 드셨나요?",
                answer_keywords=["김치볶음밥"],
            )

        self.assertEqual(78, saved["questionId"])
        self.assertTrue(saved["keywordUpdateFailed"])
        self.assertEqual(2, put_mock.call_count)

    def test_keyword_update_retries_once_after_transient_failure(self):
        post_response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: {"questionId": 79, "questionText": "무엇을 드셨나요?"},
        )
        put_response = SimpleNamespace(raise_for_status=lambda: None)

        with (
            patch.object(recall_generator.requests, "post", return_value=post_response, create=True),
            patch.object(
                recall_generator.requests,
                "put",
                side_effect=[TimeoutError("temporary timeout"), put_response],
                create=True,
            ) as put_mock,
        ):
            saved = recall_generator.save_recall_question_to_spring(
                user_id=2,
                memory_point="김치볶음밥을 먹은 일",
                question_text="무엇을 드셨나요?",
                answer_keywords=["김치볶음밥"],
            )

        self.assertEqual(["김치볶음밥"], saved["keywords"])
        self.assertNotIn("keywordUpdateFailed", saved)
        self.assertEqual(2, put_mock.call_count)

    def test_keyword_matching_ignores_spacing_variation(self):
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "김치 볶음밥",
                "김치볶음밥 먹었어",
            )
        )
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "미스터 트롯",
                "미스터트롯 봤어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "아들",
                "딸이 왔어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "미스터 트롯",
                "미스터트롯쇼를 봤어",
            )
        )

    def test_keyword_matching_treats_punctuation_as_word_boundary(self):
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "김치볶음밥",
                "김치볶음밥,먹었어요",
            )
        )
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "아들",
                "아들이랑,통화했어요",
            )
        )

    def test_keyword_matching_does_not_use_unrelated_substrings(self):
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "공원",
                "공원묘지에 다녀왔어",
            )
        )
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "공원",
                "공원에 다녀왔어",
            )
        )

    def test_single_syllable_keyword_uses_token_match(self):
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "약",
                "아침에 약을 먹었어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "약",
                "병원 예약을 했어",
            )
        )
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "딸",
                "딸과 통화했어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "사",
                "사과를 먹었어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "약",
                "약과 먹었어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "국",
                "국가 대표를 봤어",
            )
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched(
                "길",
                "길었어",
            )
        )
        self.assertTrue(
            recall_score_calculator.is_keyword_matched(
                "약과",
                "약과 먹었어",
            )
        )

    def test_single_syllable_keyword_handles_polite_noun_endings(self):
        for answer in ("약이요", "약이에요", "약입니다"):
            with self.subTest(answer=answer):
                self.assertTrue(
                    recall_score_calculator.is_keyword_matched("약", answer)
                )

    def test_single_syllable_keyword_allows_particles_in_natural_answers(self):
        cases = (
            ("차", "차도 마셨어"),
            ("길", "길이 막혔어"),
            ("물", "물도 마셨어"),
        )

        for keyword, answer in cases:
            with self.subTest(answer=answer):
                self.assertTrue(
                    recall_score_calculator.is_keyword_matched(keyword, answer)
                )
                self.assertTrue(
                    recall_generator._keyword_occurs_in_text(keyword, answer)
                )

    def test_keyword_matching_handles_past_copula_endings(self):
        cases = (
            ("대구", "대구였어"),
            ("부산", "부산이었어"),
            ("공원", "공원이었어요"),
            ("딸", "딸이었어"),
            ("피자", "피자였어요"),
        )

        for keyword, answer in cases:
            with self.subTest(answer=answer):
                self.assertTrue(
                    recall_score_calculator.is_keyword_matched(keyword, answer)
                )
                self.assertTrue(
                    recall_generator._keyword_occurs_in_text(keyword, answer)
                )

    def test_keyword_matching_handles_casual_copula_endings(self):
        cases = (
            ("딸", "딸이야"),
            ("공원", "공원이야"),
            ("피자", "피자야"),
            ("대구", "대구야"),
            ("피자", "피자였지"),
            ("아들", "아들이지"),
            ("공원", "공원이었죠"),
            ("대구", "대구였죠"),
        )

        for keyword, answer in cases:
            with self.subTest(answer=answer):
                self.assertTrue(
                    recall_score_calculator.is_keyword_matched(keyword, answer)
                )
                self.assertTrue(
                    recall_generator._keyword_occurs_in_text(keyword, answer)
                )

        self.assertTrue(
            recall_score_calculator.is_keyword_matched("대야", "대야를 샀어")
        )
        self.assertFalse(
            recall_score_calculator.is_keyword_matched("대", "대야를 샀어")
        )

    def test_keyword_matching_handles_common_companion_particles(self):
        cases = (
            ("동생", "동생하고 통화했어"),
            ("아들", "아들한테 전화했어"),
            ("친구", "친구랑 만났어"),
            ("딸", "딸하고 이야기했어"),
            ("공원", "공원까지 다녀왔어"),
            ("어머니", "어머니께 전화드렸어"),
            ("아버지", "아버지께서 오셨어"),
        )

        for keyword, answer in cases:
            with self.subTest(keyword=keyword, answer=answer):
                self.assertTrue(
                    recall_score_calculator.is_keyword_matched(keyword, answer)
                )
                self.assertTrue(
                    recall_generator._keyword_occurs_in_text(keyword, answer)
                )

    def test_negated_or_corrected_answer_does_not_receive_keyword_credit(self):
        answers = (
            "김치볶음밥은 안 먹었어",
            "김치볶음밥은 먹어본 적 없어",
            "김치볶음밥은 먹은 적이 없어",
            "김치볶음밥은 먹지 못했어",
            "김치볶음밥이 아니라 라면이야",
            "김치볶음밥 말고 피자였어",
            "김치볶음밥은 아닌 것 같아",
            "기억이 안 나",
        )

        for answer in answers:
            with self.subTest(answer=answer):
                self.assertEqual(
                    0.0,
                    recall_score_calculator.calculate_keyword_score(
                        ["김치볶음밥"],
                        answer,
                    ),
                )

    def test_uncertainty_only_removes_credit_from_the_affected_keyword(self):
        for answer in (
            "김치볶음밥인지 모르겠어",
            "아마 김치볶음밥인 것 같아",
            "김치볶음밥인 것 같아요",
            "김치볶음밥 같아",
            "김치볶음밥이라고 확실하지 않아",
            "김치볶음밥일지도 몰라",
        ):
            with self.subTest(answer=answer):
                self.assertEqual(
                    0.0,
                    recall_score_calculator.calculate_keyword_score(
                        ["김치볶음밥"],
                        answer,
                    ),
                )

        for answer in (
            "누구와 먹었는지는 모르겠지만 김치볶음밥은 기억나",
            "김치볶음밥은 기억나는데 누구와 먹었는지는 모르겠어",
            "김치볶음밥인 것 같지만 확실히 김치볶음밥이야",
        ):
            with self.subTest(answer=answer):
                self.assertEqual(
                    100.0,
                    recall_score_calculator.calculate_keyword_score(
                        ["김치볶음밥"],
                        answer,
                    ),
                )

    def test_positive_answer_still_receives_keyword_credit(self):
        for answer in (
            "김치볶음밥이요",
            "아니에요, 김치볶음밥이에요",
            "라면이 아니라 김치볶음밥이에요",
            "김치볶음밥 먹었어, 라면은 아니야",
            "김치볶음밥 먹었어. 그리고 피자는 아니야",
            "김치볶음밥 먹었어, 라면은 안 먹었어",
            "라면은 안 먹고 김치볶음밥 먹었어",
            "김치볶음밥 먹었고 라면은 아니야",
            "김치볶음밥은 맞고 라면은 아니야",
            "라면은 아니고 김치볶음밥이야",
        ):
            with self.subTest(answer=answer):
                self.assertEqual(
                    100.0,
                    recall_score_calculator.calculate_keyword_score(
                        ["김치볶음밥"],
                        answer,
                    ),
                )

    def test_last_explicit_correction_decides_keyword_credit(self):
        corrected_to_answer = (
            "김치볶음밥은 아니라고 했지만 다시 생각해 보니 김치볶음밥이야",
            "기억이 안 났는데 다시 생각해보니 김치볶음밥이에요",
            "아니요, 김치볶음밥이에요",
            "라면이야 아니 김치볶음밥이야",
        )
        corrected_away_from_answer = (
            "김치볶음밥이라고 했지만 다시 생각해 보니 라면이야",
            "김치볶음밥이야. 아니, 피자였어",
            "김치볶음밥이라고 했는데 정정할게요 라면이었어요",
            "김치볶음밥이야 아니 피자였어",
        )

        for answer in corrected_to_answer:
            with self.subTest(answer=answer):
                self.assertEqual(
                    100.0,
                    recall_score_calculator.calculate_keyword_score(
                        ["김치볶음밥"],
                        answer,
                    ),
                )

        for answer in corrected_away_from_answer:
            with self.subTest(answer=answer):
                self.assertEqual(
                    0.0,
                    recall_score_calculator.calculate_keyword_score(
                        ["김치볶음밥"],
                        answer,
                    ),
                )

    def test_similarity_uses_only_the_final_corrected_answer(self):
        with patch.object(
            recall_score_calculator,
            "calculate_similarity_score",
            return_value=20.0,
        ) as similarity_mock:
            recall_score_calculator.calculate_final_recall_score(
                "김치볶음밥을 먹은 일",
                "김치볶음밥이야 아니 피자였어",
                ["김치볶음밥"],
                "RECALL",
            )

        self.assertEqual(
            "피자였어",
            similarity_mock.call_args.kwargs["current_text"],
        )

    def test_final_score_separates_confirmed_and_negated_answers(self):
        with patch.object(
            recall_score_calculator,
            "calculate_similarity_score",
            return_value=40.0,
        ):
            confirmed = recall_score_calculator.calculate_final_recall_score(
                "김치볶음밥을 먹은 일",
                "김치볶음밥이요",
                ["김치볶음밥"],
                "RECALL",
            )
            corrected = recall_score_calculator.calculate_final_recall_score(
                "김치볶음밥을 먹은 일",
                "라면이 아니라 김치볶음밥이야",
                ["김치볶음밥"],
                "RECALL",
            )
            negated = recall_score_calculator.calculate_final_recall_score(
                "김치볶음밥을 먹은 일",
                "김치볶음밥은 안 먹었어",
                ["김치볶음밥"],
                "RECALL",
            )
            unknown = recall_score_calculator.calculate_final_recall_score(
                "김치볶음밥을 먹은 일",
                "기억이 안 나",
                ["김치볶음밥"],
                "RECALL",
            )

        self.assertEqual(76.0, confirmed["finalRecallScore"])
        self.assertEqual(76.0, corrected["finalRecallScore"])
        self.assertEqual(16.0, negated["finalRecallScore"])
        self.assertEqual(16.0, unknown["finalRecallScore"])

        self.assertFalse(
            recall_score_calculator.is_keyword_matched("약", "예약이요")
        )

    def test_five_turn_session_completes_recall_and_starts_new_cycle(self):
        records = []
        next_question_id = 200

        def fetch_records(_session_id):
            return [dict(record) for record in records]

        def save_reply(record_id, reply_text):
            target = next(record for record in records if record["recordId"] == record_id)
            target["aiReplyText"] = reply_text

        def link_question(record_id, recall_question_id, answer_role):
            target = next(record for record in records if record["recordId"] == record_id)
            target["recallQuestionId"] = recall_question_id
            target["answerRole"] = answer_role
            return True

        def create_recall(**_kwargs):
            return {
                "status": "CREATED",
                "question": "조금 전에 드신 음식이 무엇이었나요?",
                "memoryPoint": "김치볶음밥을 먹은 일",
                "sourceText": "점심에 김치볶음밥을 먹었어",
                "answerKeywords": ["김치볶음밥"],
                "savedQuestion": {"questionId": next_question_id},
            }

        def use_fallback(**kwargs):
            return {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            }

        with (
            patch.object(handler, "_fetch_session_records", side_effect=fetch_records),
            patch.object(handler, "_is_fixed_questions_done_today", return_value=True),
            patch.object(handler, "_save_ai_reply", side_effect=save_reply),
            patch.object(handler, "_link_recall_question", side_effect=link_question),
            patch.object(
                handler,
                "_find_recall_question_text",
                return_value="조금 전에 드신 음식이 무엇이었나요?",
            ),
            patch.object(handler, "generate_and_save_recall_question", side_effect=create_recall),
            patch.object(handler, "generate_safe_followup_question", side_effect=use_fallback),
        ):
            user_answers = [
                "점심에 김치볶음밥을 먹었어",
                "오후에 동생이랑 통화했어",
                "저녁에는 병원에 다녀왔어",
                "김치볶음밥",
                "텔레비전에서 뉴스를 봤어",
            ]

            for turn_order, answer in enumerate(user_answers, start=1):
                records.append(
                    {
                        "recordId": turn_order,
                        "turnOrder": turn_order,
                        "transcriptText": answer,
                        "aiReplyText": "",
                        "answerRole": None,
                        "recallQuestionId": None,
                    }
                )
                handler.process_voice_reply(
                    record_id=turn_order,
                    session_id=50,
                    user_id=2,
                    transcript_text=answer,
                )

        self.assertEqual("INITIAL", records[0]["answerRole"])
        self.assertEqual(next_question_id, records[0]["recallQuestionId"])
        self.assertEqual("RECALL", records[3]["answerRole"])
        self.assertEqual(next_question_id, records[3]["recallQuestionId"])
        self.assertIn("그렇군요", records[3]["aiReplyText"])
        self.assertEqual(
            ["텔레비전에서 뉴스를 봤어"],
            handler._extract_recall_candidate_transcripts(records),
        )

    def test_twenty_four_turn_session_keeps_six_recall_cycles_separate(self):
        records = []
        recall_calls = []

        def fetch_records(_session_id):
            return [dict(record) for record in records]

        def save_reply(record_id, reply_text):
            target = next(record for record in records if record["recordId"] == record_id)
            target["aiReplyText"] = reply_text

        def link_question(record_id, recall_question_id, answer_role):
            target = next(record for record in records if record["recordId"] == record_id)
            target["recallQuestionId"] = recall_question_id
            target["answerRole"] = answer_role
            return True

        def create_recall(conversation_history, **_kwargs):
            recall_index = len(recall_calls) + 1
            question_id = 300 + recall_index
            source_text = conversation_history[0]
            recall_calls.append(question_id)
            return {
                "status": "CREATED",
                "question": f"{recall_index}번째로 이야기한 음식이 무엇이었나요?",
                "memoryPoint": source_text,
                "sourceText": source_text,
                "answerKeywords": [f"음식{recall_index}"],
                "savedQuestion": {"questionId": question_id},
            }

        def use_fallback(**kwargs):
            return {
                "nextQuestion": kwargs["fallback_question"],
                "shouldChangeTopic": False,
                "reason": "simulation",
            }

        with (
            patch.object(handler, "_fetch_session_records", side_effect=fetch_records),
            patch.object(handler, "_is_fixed_questions_done_today", return_value=True),
            patch.object(handler, "_save_ai_reply", side_effect=save_reply),
            patch.object(handler, "_link_recall_question", side_effect=link_question),
            patch.object(
                handler,
                "_find_recall_question_text",
                side_effect=lambda _user_id, question_id: f"{question_id - 300}번째로 이야기한 음식이 무엇이었나요?",
            ),
            patch.object(handler, "generate_and_save_recall_question", side_effect=create_recall),
            patch.object(handler, "generate_safe_followup_question", side_effect=use_fallback),
        ):
            for cycle in range(1, 7):
                free_answers = [
                    f"점심에 음식{cycle}을 먹었어",
                    f"오후에 친구{cycle}과 통화했어",
                    f"저녁에 방송{cycle}을 봤어",
                ]

                for answer in free_answers:
                    record_id = len(records) + 1
                    records.append(
                        {
                            "recordId": record_id,
                            "turnOrder": record_id,
                            "transcriptText": answer,
                            "aiReplyText": "",
                            "answerRole": None,
                            "recallQuestionId": None,
                        }
                    )
                    handler.process_voice_reply(
                        record_id=record_id,
                        session_id=60,
                        user_id=2,
                        transcript_text=answer,
                    )

                recall_record_id = len(records) + 1
                records.append(
                    {
                        "recordId": recall_record_id,
                        "turnOrder": recall_record_id,
                        "transcriptText": f"음식{cycle}",
                        "aiReplyText": "",
                        "answerRole": None,
                        "recallQuestionId": None,
                    }
                )
                handler.process_voice_reply(
                    record_id=recall_record_id,
                    session_id=60,
                    user_id=2,
                    transcript_text=f"음식{cycle}",
                )

        self.assertEqual(24, len(records))
        self.assertEqual(6, len(recall_calls))
        self.assertEqual(6, handler._count_completed_recall_answers(records))
        self.assertEqual([], handler._extract_recall_candidate_transcripts(records))
        self.assertIsNone(
            handler._find_pending_recall_question_id(
                records,
                current_record_id=25,
            )
        )

        initial_records = [record for record in records if record["answerRole"] == "INITIAL"]
        recall_records = [record for record in records if record["answerRole"] == "RECALL"]
        self.assertEqual(6, len(initial_records))
        self.assertEqual(6, len(recall_records))

    def test_exhausted_openers_do_not_repeat_recent_questions(self):
        records = []
        generated_questions = []

        for turn_order in range(1, 46):
            records.append(
                {
                    "recordId": turn_order,
                    "turnOrder": turn_order,
                    "transcriptText": f"회상 답변 {turn_order}",
                    "aiReplyText": "",
                    "answerRole": "RECALL",
                    "recallQuestionId": turn_order,
                }
            )
            question = handler._get_next_normal_question(
                0,
                records,
                latest_text=f"회상 답변 {turn_order}",
            )
            records[-1]["aiReplyText"] = question
            generated_questions.append(question)

        for index, question in enumerate(generated_questions):
            recent_questions = generated_questions[max(0, index - 8):index]
            self.assertNotIn(question, recent_questions)
            self.assertFalse(
                free_talk_generator.is_similar_to_previous_question(
                    question,
                    recent_questions,
                )
            )

    def test_initial_role_links_to_memory_source_record(self):
        records = [
            {
                "recordId": index,
                "turnOrder": index,
                "transcriptText": f"고정 답변 {index}",
                "aiReplyText": f"고정 질문 {index + 1}",
                "answerRole": "FIXED",
                "recallQuestionId": index,
            }
            for index in range(1, 6)
        ]
        records.extend(
            [
                {
                    "recordId": 6,
                    "turnOrder": 6,
                    "transcriptText": "점심에 김치볶음밥을 먹었어",
                    "aiReplyText": "누구와 같이 드셨어요?",
                    "answerRole": None,
                    "recallQuestionId": None,
                },
                {
                    "recordId": 7,
                    "turnOrder": 7,
                    "transcriptText": "오후에 병원에 다녀왔어",
                    "aiReplyText": "병원에서 기억나는 게 있으세요?",
                    "answerRole": None,
                    "recallQuestionId": None,
                },
                {
                    "recordId": 8,
                    "turnOrder": 8,
                    "transcriptText": "저녁에는 텔레비전을 봤어",
                    "aiReplyText": "",
                    "answerRole": None,
                    "recallQuestionId": None,
                },
            ]
        )
        linked = []
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "generate_and_save_recall_question",
                return_value={
                    "status": "CREATED",
                    "question": "아까 드신 음식이 무엇이었나요?",
                    "memoryPoint": "김치볶음밥을 먹은 일",
                    "sourceText": "점심에 김치볶음밥을 먹었어",
                    "savedQuestion": {"questionId": 99},
                },
            ),
            patch.object(handler, "_link_recall_question", side_effect=lambda **kwargs: linked.append(kwargs) or True),
            patch.object(handler, "_save_ai_reply", side_effect=lambda **kwargs: saved.append(kwargs)),
        ):
            handler.process_voice_reply(
                record_id=8,
                session_id=30,
                user_id=2,
                transcript_text="저녁에는 텔레비전을 봤어",
            )

        self.assertEqual(6, linked[-1]["record_id"])
        self.assertEqual("INITIAL", linked[-1]["answer_role"])
        self.assertEqual("아까 드신 음식이 무엇이었나요?", saved[-1]["reply_text"])

    def test_recall_api_failure_falls_back_to_free_talk(self):
        records = [
            {
                "recordId": index,
                "turnOrder": index,
                "transcriptText": f"고정 답변 {index}",
                "aiReplyText": f"고정 질문 {index + 1}",
                "answerRole": "FIXED",
                "recallQuestionId": index,
            }
            for index in range(1, 6)
        ]
        records.extend(
            [
                {"recordId": 6, "turnOrder": 6, "transcriptText": "김치볶음밥 먹었어", "aiReplyText": "", "answerRole": None},
                {"recordId": 7, "turnOrder": 7, "transcriptText": "병원에 다녀왔어", "aiReplyText": "", "answerRole": None},
                {"recordId": 8, "turnOrder": 8, "transcriptText": "텔레비전 봤어", "aiReplyText": "", "answerRole": None},
            ]
        )
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "generate_and_save_recall_question",
                side_effect=TimeoutError("simulated timeout"),
            ),
            patch.object(
                handler,
                "generate_safe_followup_question",
                side_effect=lambda **kwargs: {
                    "nextQuestion": kwargs["fallback_question"],
                    "shouldChangeTopic": False,
                    "reason": "simulation",
                },
            ),
            patch.object(handler, "_save_ai_reply", side_effect=lambda **kwargs: saved.append(kwargs)),
        ):
            handler.process_voice_reply(
                record_id=8,
                session_id=31,
                user_id=2,
                transcript_text="텔레비전 봤어",
            )

        self.assertEqual(1, len(saved))
        self.assertTrue(saved[0]["reply_text"].endswith("?"))

    def test_recall_link_retries_once_after_transient_failure(self):
        successful_response = SimpleNamespace(raise_for_status=lambda: None)

        with patch.object(
            handler.requests,
            "post",
            side_effect=[TimeoutError("temporary timeout"), successful_response],
            create=True,
        ) as post_mock:
            linked = handler._link_recall_question(
                record_id=10,
                recall_question_id=20,
                answer_role="INITIAL",
            )

        self.assertTrue(linked)
        self.assertEqual(2, post_mock.call_count)

    def test_ai_reply_save_retries_once_after_transient_failure(self):
        successful_response = SimpleNamespace(raise_for_status=lambda: None)

        with patch.object(
            handler.requests,
            "post",
            side_effect=[TimeoutError("temporary timeout"), successful_response],
            create=True,
        ) as post_mock:
            handler._save_ai_reply(
                record_id=10,
                reply_text="오늘은 무엇을 하셨어요?",
            )

        self.assertEqual(2, post_mock.call_count)

    def test_session_record_fetch_retries_once_after_transient_failure(self):
        successful_response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: [{"recordId": 10}],
        )

        with patch.object(
            handler.requests,
            "get",
            side_effect=[TimeoutError("temporary timeout"), successful_response],
            create=True,
        ) as get_mock:
            records = handler._fetch_session_records(session_id=90)

        self.assertEqual([{"recordId": 10}], records)
        self.assertEqual(2, get_mock.call_count)

    def test_recall_question_fetch_retries_once_after_transient_failure(self):
        successful_response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: [{"questionId": 99, "questionText": "무엇을 드셨나요?"}],
        )

        with patch.object(
            handler.requests,
            "get",
            side_effect=[TimeoutError("temporary timeout"), successful_response],
            create=True,
        ) as get_mock:
            questions = handler._fetch_recall_questions(user_id=2)

        self.assertEqual(99, questions[0]["questionId"])
        self.assertEqual(2, get_mock.call_count)

    def test_missing_session_records_do_not_guess_answer_role(self):
        with (
            patch.object(handler, "_fetch_session_records", return_value=[]),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=AssertionError("missing session must not save a guessed reply"),
            ),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("missing session must not guess a role"),
            ),
        ):
            handler.process_voice_reply(
                record_id=10,
                session_id=90,
                user_id=2,
                transcript_text="김치볶음밥",
            )

    def test_fixed_daily_status_retries_once_after_transient_failure(self):
        successful_response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: {"data": True},
        )

        with patch.object(
            handler.requests,
            "get",
            side_effect=[TimeoutError("temporary timeout"), successful_response],
            create=True,
        ) as get_mock:
            completed = handler._is_fixed_questions_done_today(user_id=2)

        self.assertTrue(completed)
        self.assertEqual(2, get_mock.call_count)

    def test_fixed_daily_completion_retries_once_after_transient_failure(self):
        successful_response = SimpleNamespace(raise_for_status=lambda: None)

        with patch.object(
            handler.requests,
            "post",
            side_effect=[TimeoutError("temporary timeout"), successful_response],
            create=True,
        ) as post_mock:
            completed = handler._mark_fixed_questions_done_today(user_id=2)

        self.assertTrue(completed)
        self.assertEqual(2, post_mock.call_count)

    def test_fixed_link_failure_does_not_advance_to_next_question(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "김경빈입니다",
                "aiReplyText": "",
                "answerRole": None,
                "recallQuestionId": None,
            }
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(handler, "_is_fixed_questions_done_today", return_value=False),
            patch.object(
                handler,
                "_find_or_create_fixed_question",
                return_value={"questionId": 1},
            ),
            patch.object(handler, "_link_recall_question", return_value=False),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=1,
                session_id=70,
                user_id=2,
                transcript_text="김경빈입니다",
            )

        self.assertEqual([], saved)

    def test_pending_recall_link_failure_does_not_advance_conversation(self):
        records = [
            {
                "recordId": 6,
                "turnOrder": 6,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "아까 드신 음식이 무엇이었나요?",
                "answerRole": "INITIAL",
                "recallQuestionId": 99,
            },
            {
                "recordId": 7,
                "turnOrder": 7,
                "transcriptText": "김치볶음밥",
                "aiReplyText": "",
                "answerRole": None,
                "recallQuestionId": None,
            },
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_find_recall_question_text",
                return_value="아까 드신 음식이 무엇이었나요?",
            ),
            patch.object(handler, "_link_recall_question", return_value=False),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=7,
                session_id=71,
                user_id=2,
                transcript_text="김치볶음밥",
            )

        self.assertEqual([], saved)

    def test_unshown_pending_recall_question_is_presented_before_linking_answer(self):
        recall_question = "아까 드신 음식이 무엇이었나요?"
        records = [
            {
                "recordId": 6,
                "turnOrder": 6,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "그 음식은 어디에서 드셨어요?",
                "answerRole": "INITIAL",
                "recallQuestionId": 99,
            },
            {
                "recordId": 7,
                "turnOrder": 7,
                "transcriptText": "집에서 먹었어",
                "aiReplyText": "",
                "answerRole": None,
                "recallQuestionId": None,
            },
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_find_recall_question_text",
                return_value=recall_question,
            ),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("unshown question must not consume current answer"),
            ),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=7,
                session_id=73,
                user_id=2,
                transcript_text="집에서 먹었어",
            )

        self.assertEqual(
            [{"record_id": 7, "reply_text": recall_question}],
            saved,
        )

    def test_linked_initial_record_recovers_missing_recall_question(self):
        recall_question = "아까 드신 음식이 무엇이었나요?"
        records = [
            {
                "recordId": 8,
                "turnOrder": 8,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "",
                "answerRole": "INITIAL",
                "recallQuestionId": 100,
            }
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_find_recall_question_text",
                return_value=recall_question,
            ),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("initial record must not be relinked"),
            ),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=8,
                session_id=74,
                user_id=2,
                transcript_text="점심에 김치볶음밥을 먹었어",
            )

        self.assertEqual(
            [{"record_id": 8, "reply_text": recall_question}],
            saved,
        )

    def test_initial_link_failure_hides_untracked_recall_question(self):
        records = [
            {
                "recordId": index,
                "turnOrder": index,
                "transcriptText": f"고정 답변 {index}",
                "aiReplyText": f"고정 질문 {index + 1}",
                "answerRole": "FIXED",
                "recallQuestionId": index,
            }
            for index in range(1, 6)
        ]
        records.extend(
            [
                {"recordId": 6, "turnOrder": 6, "transcriptText": "김치볶음밥 먹었어", "aiReplyText": "", "answerRole": None},
                {"recordId": 7, "turnOrder": 7, "transcriptText": "동생과 통화했어", "aiReplyText": "", "answerRole": None},
                {"recordId": 8, "turnOrder": 8, "transcriptText": "텔레비전 봤어", "aiReplyText": "", "answerRole": None},
            ]
        )
        saved = []
        recall_question = "아까 드신 음식이 무엇이었나요?"

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "generate_and_save_recall_question",
                return_value={
                    "status": "CREATED",
                    "question": recall_question,
                    "memoryPoint": "김치볶음밥을 먹은 일",
                    "sourceText": "김치볶음밥 먹었어",
                    "savedQuestion": {"questionId": 120},
                },
            ),
            patch.object(handler, "_link_recall_question", return_value=False),
            patch.object(
                handler,
                "generate_safe_followup_question",
                side_effect=lambda **kwargs: {
                    "nextQuestion": kwargs["fallback_question"],
                    "shouldChangeTopic": False,
                    "reason": "simulation",
                },
            ),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=8,
                session_id=72,
                user_id=2,
                transcript_text="텔레비전 봤어",
            )

        self.assertEqual(1, len(saved))
        self.assertNotEqual(recall_question, saved[0]["reply_text"])
        self.assertTrue(saved[0]["reply_text"].endswith("?"))

    def test_duplicate_history_uses_only_recent_recall_questions(self):
        payload = [
            {
                "questionId": question_id,
                "questionText": f"회상 질문 {question_id}",
                "expectedAnswer": f"기억 단서 {question_id}",
                "category": "CONVERSATION",
            }
            for question_id in range(20, 0, -1)
        ]
        payload.append(
            {
                "questionId": 100,
                "questionText": "성함을 어떻게 불러드리면 될까요?",
                "expectedAnswer": "USER_NAME",
                "category": "INITIAL_FIXED",
            }
        )
        response = SimpleNamespace(
            raise_for_status=lambda: None,
            json=lambda: payload,
        )

        with patch.object(recall_generator.requests, "get", return_value=response, create=True):
            result = recall_generator.fetch_existing_recall_questions(user_id=2)

        self.assertEqual(12, len(result["previousQuestions"]))
        self.assertEqual("회상 질문 9", result["previousQuestions"][0])
        self.assertEqual("회상 질문 20", result["previousQuestions"][-1])
        self.assertNotIn("USER_NAME", result["usedMemoryPoints"])

    def test_pending_recall_answer_is_linked_once_and_returns_to_free_talk(self):
        records = [
            {
                "recordId": 6,
                "turnOrder": 6,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "아까 드신 음식이 무엇이었나요?",
                "answerRole": "INITIAL",
                "recallQuestionId": 99,
            },
            {
                "recordId": 7,
                "turnOrder": 7,
                "transcriptText": "김치볶음밥",
                "aiReplyText": "",
                "answerRole": None,
                "recallQuestionId": None,
            },
        ]
        linked = []
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_find_recall_question_text",
                return_value="아까 드신 음식이 무엇이었나요?",
            ),
            patch.object(handler, "_link_recall_question", side_effect=lambda **kwargs: linked.append(kwargs) or True),
            patch.object(handler, "_save_ai_reply", side_effect=lambda **kwargs: saved.append(kwargs)),
        ):
            handler.process_voice_reply(
                record_id=7,
                session_id=32,
                user_id=2,
                transcript_text="김치볶음밥",
            )

        self.assertEqual(1, len(linked))
        self.assertEqual("RECALL", linked[0]["answer_role"])
        self.assertEqual(99, linked[0]["recall_question_id"])
        self.assertEqual(1, len(saved))
        self.assertTrue(saved[0]["reply_text"].endswith("?"))

        completed_records = handler._with_current_answer_role(
            records,
            current_record_id=7,
            answer_role="RECALL",
            recall_question_id=99,
        )
        self.assertIsNone(
            handler._find_pending_recall_question_id(
                completed_records,
                current_record_id=8,
            )
        )

    def test_record_with_saved_ai_reply_is_not_processed_twice(self):
        records = [
            {
                "recordId": 10,
                "turnOrder": 10,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "그 음식은 어디에서 드셨어요?",
                "answerRole": None,
                "recallQuestionId": None,
            }
        ]

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=AssertionError("saved reply must not be overwritten"),
            ),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("saved record must not be relinked"),
            ),
            patch.object(
                handler,
                "generate_and_save_recall_question",
                side_effect=AssertionError("duplicate recall question must not be generated"),
            ),
        ):
            handler.process_voice_reply(
                record_id=10,
                session_id=80,
                user_id=2,
                transcript_text="점심에 김치볶음밥을 먹었어",
            )

        self.assertEqual(
            "그 음식은 어디에서 드셨어요?",
            records[0]["aiReplyText"],
        )

    def test_linked_fixed_answer_resumes_without_relinking(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "김경빈입니다",
                "aiReplyText": "",
                "answerRole": "FIXED",
                "recallQuestionId": 1,
            }
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("linked fixed answer must not be relinked"),
            ),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=1,
                session_id=81,
                user_id=2,
                transcript_text="김경빈입니다",
            )

        self.assertEqual(1, len(saved))
        self.assertEqual(
            handler.FIXED_QUESTIONS[1]["questionText"],
            saved[0]["reply_text"],
        )

    def test_last_linked_fixed_answer_resumes_free_talk_transition(self):
        records = [
            {
                "recordId": index,
                "turnOrder": index,
                "transcriptText": f"고정 답변 {index}",
                "aiReplyText": (
                    "" if index == 5 else handler.FIXED_QUESTIONS[index]["questionText"]
                ),
                "answerRole": "FIXED",
                "recallQuestionId": index,
            }
            for index in range(1, 6)
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("linked fixed answer must not be relinked"),
            ),
            patch.object(handler, "_mark_fixed_questions_done_today", return_value=True) as mark_mock,
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=5,
                session_id=82,
                user_id=2,
                transcript_text="6월 10일입니다",
            )

        mark_mock.assert_called_once_with(2)
        self.assertEqual(1, len(saved))
        self.assertIn(saved[0]["reply_text"], handler.SAFE_OPENING_QUESTIONS)

    def test_linked_recall_answer_resumes_transition_without_relinking(self):
        records = [
            {
                "recordId": 6,
                "turnOrder": 6,
                "transcriptText": "점심에 김치볶음밥을 먹었어",
                "aiReplyText": "아까 드신 음식이 무엇이었나요?",
                "answerRole": "INITIAL",
                "recallQuestionId": 99,
            },
            {
                "recordId": 7,
                "turnOrder": 7,
                "transcriptText": "김치볶음밥",
                "aiReplyText": "",
                "answerRole": "RECALL",
                "recallQuestionId": 99,
            },
        ]
        saved = []

        with (
            patch.object(handler, "_fetch_session_records", return_value=records),
            patch.object(handler, "_is_fixed_questions_done_today", return_value=True),
            patch.object(
                handler,
                "_link_recall_question",
                side_effect=AssertionError("linked recall answer must not be relinked"),
            ),
            patch.object(
                handler,
                "_save_ai_reply",
                side_effect=lambda **kwargs: saved.append(kwargs),
            ),
        ):
            handler.process_voice_reply(
                record_id=7,
                session_id=83,
                user_id=2,
                transcript_text="김치볶음밥",
            )

        self.assertEqual(1, len(saved))
        self.assertTrue(saved[0]["reply_text"].endswith("?"))
        self.assertIn("그렇군요", saved[0]["reply_text"])


    def test_recall_analysis_sends_only_target_pair_but_scores_all_pairs(self):
        records = [
            {
                "recordId": 10,
                "recallQuestionId": 1,
                "answerRole": "INITIAL",
                "transcriptText": "first memory",
            },
            {
                "recordId": 11,
                "recallQuestionId": 1,
                "answerRole": "RECALL",
                "transcriptText": "first answer",
            },
            {
                "recordId": 20,
                "recallQuestionId": 2,
                "answerRole": "INITIAL",
                "transcriptText": "second memory",
            },
            {
                "recordId": 21,
                "recallQuestionId": 2,
                "answerRole": "RECALL",
                "transcriptText": "second answer",
            },
        ]
        questions = {
            1: {"expectedAnswer": "first memory"},
            2: {"expectedAnswer": "second memory"},
        }
        recall_posts = []
        risk_posts = []
        score_values = iter((20.0, 80.0))

        def calculate_score(**_kwargs):
            score = next(score_values)
            return {
                "similarityScore": score,
                "keywordScore": score,
                "finalRecallScore": score,
            }

        ok_response = SimpleNamespace(
            status_code=200,
            text="ok",
            raise_for_status=lambda: None,
        )

        with (
            patch.object(recall_api_client, "get_session_records", return_value=records),
            patch.object(recall_api_client, "get_recall_questions", return_value=questions),
            patch.object(
                recall_api_client,
                "calculate_initial_fixed_score",
                return_value=100.0,
            ),
            patch.object(
                recall_api_client,
                "calculate_final_recall_score",
                side_effect=calculate_score,
            ),
            patch.object(
                recall_api_client,
                "send_recall_result",
                side_effect=lambda **kwargs: (recall_posts.append(kwargs) or ok_response),
            ),
            patch.object(
                recall_api_client,
                "send_risk_result",
                side_effect=lambda **kwargs: (risk_posts.append(kwargs) or ok_response),
            ),
        ):
            recall_api_client.analyze_session_recall(
                user_id=2,
                session_id=3,
                speech_risk_score=10.0,
                target_recall_record_id=21,
                send_all_recall_results=False,
            )

        self.assertEqual([21], [post["current_record_id"] for post in recall_posts])
        self.assertEqual(50.0, risk_posts[0]["recall_score"])

    def test_explicit_drink_does_not_inherit_previous_health_topic(self):
        self.assertEqual(
            "FOOD",
            handler._detect_conversation_topic(
                "물도 마셨어",
                ["병원에는 못 갔지만 약은 먹었어", "물도 마셨어"],
                "약 드신 뒤에는 몸이 좀 어떠셨어요?",
            ),
        )

    def test_negated_hospital_visit_does_not_allow_visit_followup(self):
        candidates = handler._get_topic_aware_fallback_candidates(
            "ANCHOR",
            "이제 좀 나아",
            ["병원에는 못 갔지만 약은 먹었어", "이제 좀 나아"],
            "약 드신 뒤에는 몸이 좀 어떠셨어요?",
        )

        self.assertTrue(candidates)
        self.assertTrue(all("병원" not in question for question in candidates))

    def test_cooking_soup_at_home_is_food_not_unspecified_place(self):
        self.assertEqual("FOOD", handler._detect_topic_in_text("집에서 국을 끓였어"))

    def test_meeting_at_a_place_keeps_person_context(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "동생을 만났어",
                "aiReplyText": "그분이 생각날 때 가장 먼저 떠오르는 모습이 있으세요?",
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "친구를 만났어",
                "aiReplyText": "그분과 함께했던 일 중에 기억나는 장면이 있으세요?",
            },
            {
                "recordId": 3,
                "turnOrder": 3,
                "transcriptText": "공원에서 만났어",
                "aiReplyText": "",
            },
        ]

        question = handler._get_next_normal_question(
            3,
            records,
            latest_text="공원에서 만났어",
        )

        self.assertIn("그분", question)
        self.assertIn("그곳", question)
        self.assertNotIn("집에서는", question)

    def test_food_wish_question_does_not_sound_like_recall_test(self):
        candidates = handler._get_topic_aware_fallback_candidates(
            "ANCHOR",
            "같이 밥 먹고 싶어",
            ["손주가 보고 싶어", "같이 밥 먹고 싶어"],
            "그분은 언제쯤 만나실 예정이세요?",
        )

        self.assertTrue(candidates)
        self.assertTrue(all("기억" not in question for question in candidates))
        self.assertTrue(all("떠올" not in question for question in candidates))

    def test_improved_health_answer_gets_relief_acknowledgement(self):
        reply = free_talk_generator.build_fallback_with_empathy(
            "지금 가장 편해진 부분이 있으세요?",
            ["이제 좀 나아"],
        )

        self.assertTrue(reply.startswith("다행이네요."))

    def test_routine_scenery_and_object_actions_have_specific_topics(self):
        cases = (
            ("저녁 시간이 기다려져", "ROUTINE"),
            ("창밖을 봤어", "SCENERY"),
            ("벚꽃이 보였어", "SCENERY"),
            ("화분을 옮겼어", "OBJECT"),
            ("창문을 열었어", "OBJECT"),
        )

        for answer, expected_topic in cases:
            with self.subTest(answer=answer):
                self.assertEqual(expected_topic, handler._detect_topic_in_text(answer))

    def test_weather_detail_from_news_keeps_media_topic(self):
        self.assertEqual(
            "MEDIA",
            handler._detect_conversation_topic(
                "날씨 소식이 기억나",
                ["텔레비전 봤어", "뉴스였어", "날씨 소식이 기억나"],
                "그걸 보실 때 기분은 어떠셨어요?",
            ),
        )

    def test_wish_questions_do_not_assume_the_event_already_happened(self):
        cases = (
            ("손주가 보고 싶어", "PERSON"),
            ("공원에 가고 싶어", "PLACE"),
            ("밥을 먹고 싶어", "FOOD"),
        )

        for answer, expected_topic in cases:
            with self.subTest(answer=answer):
                questions = handler._get_topic_aware_fallback_candidates(
                    "DEEPEN",
                    answer,
                    [answer],
                )
                self.assertTrue(questions)
                self.assertEqual(expected_topic, handler._detect_topic_in_text(answer))
                self.assertTrue(
                    all(
                        "하셨어" not in question
                        and "다녀오셨어" not in question
                        and "드셨어" not in question
                        for question in questions
                    )
                )

    def test_negative_meta_answer_does_not_replace_the_recent_place_topic(self):
        self.assertEqual(
            "PLACE",
            handler._detect_recent_context_topic(
                ["공원에 가고 싶어", "아직은 못 갔어"],
            ),
        )

    def test_future_trip_with_known_companion_asks_a_new_place_detail(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "공원에 가고 싶어",
                "aiReplyText": "그곳에 가시면 가장 먼저 무엇을 하고 싶으세요?",
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "아직은 못 갔어",
                "aiReplyText": "그러셨군요. 오늘 집에서는 주로 어떻게 시간을 보내셨어요?",
            },
            {
                "recordId": 3,
                "turnOrder": 3,
                "transcriptText": "내일 아들과 갈 거야",
                "aiReplyText": "",
            },
        ]

        question = handler._get_next_normal_question(
            3,
            records,
            latest_text="내일 아들과 갈 거야",
        )

        self.assertIn("어떻게 가실 예정", question)
        self.assertNotIn("혼자", question)
        self.assertNotIn("함께 가실 분", question)

    def test_in_person_conversation_does_not_jump_to_an_unrelated_home_topic(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "아들이 왔어",
                "aiReplyText": "그분과 함께했던 일 중에 기억나는 장면이 있으세요?",
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "같이 점심 먹었어",
                "aiReplyText": "드셨을 때 맛은 어떠셨어요?",
            },
            {
                "recordId": 3,
                "turnOrder": 3,
                "transcriptText": "거실에서 이야기했어",
                "aiReplyText": "",
            },
        ]

        question = handler._get_next_normal_question(
            3,
            records,
            latest_text="거실에서 이야기했어",
        )

        self.assertIn("그분", question)
        self.assertIn("이야기", question)
        self.assertNotIn("집에서는", question)

    def test_negated_and_future_contact_stays_with_the_person(self):
        negated_questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "요즘 연락을 못 했어",
            ["손주가 보고 싶어", "요즘 연락을 못 했어"],
            "그분을 만나면 가장 먼저 어떤 말을 하고 싶으세요?",
        )
        future_questions = handler._get_topic_aware_fallback_candidates(
            "ANCHOR",
            "다음 주에 전화할 거야",
            ["손주가 보고 싶어", "다음 주에 전화할 거야"],
            "그분께 연락하게 되면 어떤 이야기를 나누고 싶으세요?",
        )

        self.assertTrue(all("그분" in question for question in negated_questions))
        self.assertTrue(all("전화" in question or "그분" in question for question in future_questions))

    def test_correction_with_malgo_keeps_only_the_final_answer(self):
        answer = "아니 짜장면 말고 국수 먹었어"

        self.assertEqual("국수 먹었어", handler._get_final_correction_segment(answer))
        self.assertEqual(
            "국수 먹었어",
            free_talk_generator._get_final_correction_segment(answer),
        )
        self.assertEqual(
            "국수 먹었어",
            recall_generator._get_final_correction_segment(answer),
        )
        self.assertEqual(
            "국수 먹었어",
            recall_score_calculator.get_final_correction_segment(answer),
        )
        self.assertEqual(
            [answer, "친구를 만났어"],
            recall_generator.select_recall_memory_candidates(
                [answer, "친구를 만났어"],
            ),
        )

        wrong_client = self._recall_client(
            "짜장면 먹었어",
            "아까 드신 음식이 무엇이었나요?",
            answer_keyword="짜장면",
        )
        correct_client = self._recall_client(
            "국수 먹었어",
            "아까 드신 음식이 무엇이었나요?",
            answer_keyword="국수",
        )
        history = [answer, "친구를 만났어"]

        with patch.object(recall_generator, "_get_client", return_value=wrong_client):
            wrong_result = recall_generator.generate_recall_question_from_conversation(
                history
            )

        with patch.object(recall_generator, "_get_client", return_value=correct_client):
            correct_result = recall_generator.generate_recall_question_from_conversation(
                history
            )

        self.assertEqual("SKIPPED", wrong_result["status"])
        self.assertEqual("CREATED", correct_result["status"])
        self.assertEqual(answer, correct_result["sourceText"])

    def test_generic_food_action_requires_a_named_food(self):
        self.assertTrue(
            recall_generator._has_completed_generic_food_action("국수 먹었어")
        )
        self.assertFalse(
            recall_generator._has_completed_generic_food_action("그거 먹었어")
        )
        self.assertEqual(
            [],
            recall_generator.select_recall_memory_candidates(["그거 먹었어"]),
        )

    def test_recovered_recall_answer_gets_a_natural_transition(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "김치볶음밥 먹었어",
                "aiReplyText": "아까 드신 음식이 무엇이었나요?",
                "answerRole": "INITIAL",
                "recallQuestionId": 10,
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "아, 생각났어",
                "aiReplyText": "",
                "answerRole": "RECALL",
                "recallQuestionId": 10,
            },
        ]

        question = handler._get_next_normal_question(
            0,
            records,
            latest_text="아, 생각났어",
        )

        self.assertTrue(question.startswith("아하, 생각나셨군요."))
        self.assertNotIn("알겠습니다", question)

    def test_future_nap_does_not_receive_a_past_rest_question(self):
        answer = "조금 있다가 낮잠 잘 거야"
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": answer,
                "aiReplyText": "",
            }
        ]

        question = handler._get_next_normal_question(
            1,
            records,
            latest_text=answer,
        )

        self.assertTrue(handler._is_future_response(answer))
        self.assertNotIn("쉬고 나서는", question)
        self.assertNotIn("쉬셨어", question)
        self.assertIn("예정", question)

    def test_shopping_wish_is_recognized_without_assuming_a_purchase(self):
        answer = "새 신발을 사고 싶어"
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            answer,
            [answer],
        )

        self.assertEqual("SHOPPING", handler._detect_topic_in_text(answer))
        self.assertTrue(questions)
        self.assertTrue(all("사셨어" not in question for question in questions))

    def test_drink_wish_uses_drink_wording(self):
        questions = handler._get_topic_aware_fallback_candidates(
            "DEEPEN",
            "커피 마시고 싶어",
            ["커피 마시고 싶어"],
        )

        self.assertEqual(handler.FOOD_DRINK_WISH_QUESTIONS, questions)
        self.assertTrue(all("음식" not in question for question in questions))

    def test_place_detail_is_not_requested_again_after_it_was_given(self):
        records = [
            {
                "recordId": 1,
                "turnOrder": 1,
                "transcriptText": "아들을 만났어",
                "aiReplyText": "그분이 생각날 때 가장 먼저 떠오르는 모습이 있으세요?",
            },
            {
                "recordId": 2,
                "turnOrder": 2,
                "transcriptText": "공원에서 만났어",
                "aiReplyText": "그곳에서 그분과 무엇을 하셨어요?",
            },
            {
                "recordId": 3,
                "turnOrder": 3,
                "transcriptText": "한 시간 있었어",
                "aiReplyText": "",
            },
        ]

        question = handler._get_next_normal_question(
            3,
            records,
            latest_text="한 시간 있었어",
        )

        self.assertNotIn("어디", question)
        self.assertNotIn("다녀오신 곳", question)
        self.assertTrue("그곳" in question or "장소" in question or "주변" in question)

    def test_object_action_without_a_particle_survives_stt_style_speech(self):
        for answer in ("화분 옮겼어", "창문 열었어", "리모컨 뒀어"):
            with self.subTest(answer=answer):
                self.assertEqual("OBJECT", handler._detect_topic_in_text(answer))
                self.assertTrue(
                    recall_generator.score_recall_memory_candidate(answer)["isValid"]
                )

        for vague_answer in ("오늘 옮겼어", "그거 뒀어"):
            with self.subTest(vague_answer=vague_answer):
                self.assertFalse(handler._has_everyday_object_action(vague_answer))
                self.assertFalse(
                    recall_generator._has_completed_generic_object_action(vague_answer)
                )

    def test_uncertain_future_and_hearsay_are_not_memory_points(self):
        invalid_answers = (
            "공원에 갈 수도 있어",
            "친구가 병원에 갔다고 했어",
            "비가 오면 집에 있을 거야",
            "아마 약을 먹었을걸",
            "누가 텔레비전 봤대",
            "나중에 낮잠 자야지",
        )

        for answer in invalid_answers:
            with self.subTest(answer=answer):
                self.assertFalse(
                    recall_generator.score_recall_memory_candidate(answer)["isValid"]
                )

    def test_named_completed_actions_survive_without_a_fixed_keyword_list(self):
        completed_answers = (
            "반찬 꺼냈어",
            "불을 켰어",
            "신문 읽었어",
            "라디오 들었어",
            "쓰레기 버렸어",
            "사진을 봤어",
            "안경을 썼어",
            "버스를 탔어",
        )

        for answer in completed_answers:
            with self.subTest(answer=answer):
                selected = recall_generator.select_recall_memory_candidates(
                    [answer, "친구를 만났어"],
                )
                self.assertIn(answer, selected)

    def test_concrete_nouns_with_state_predicates_are_not_memory_actions(self):
        state_answers = (
            "허리가 아팠어",
            "마음이 불편했어",
            "친구가 슬펐어",
            "아들이 외로웠어",
            "꽃이 예뻤어",
            "음식이 맛있었어",
            "방송이 재미있었어",
        )

        for answer in state_answers:
            with self.subTest(answer=answer):
                self.assertFalse(
                    recall_generator.score_recall_memory_candidate(answer)["isValid"]
                )

    def test_state_context_with_a_completed_action_remains_a_memory_candidate(self):
        completed_answers = (
            "허리가 아팠지만 약은 먹었어",
            "날씨가 좋았고 공원에 갔어",
            "음식이 맛있어서 한 그릇 먹었어",
            "방송이 재미있어서 끝까지 봤어",
            "꽃이 예뻐서 사진을 찍었어",
        )

        for answer in completed_answers:
            with self.subTest(answer=answer):
                self.assertTrue(
                    recall_generator.score_recall_memory_candidate(answer)["isValid"]
                )

    def test_alternative_correction_uses_only_the_last_answer_for_scoring(self):
        corrections = (
            ("라면이 아니라 김치볶음밥이야", "김치볶음밥이야"),
            ("라면은 아니고 김치볶음밥이야", "김치볶음밥이야"),
            ("라면 말고 김치볶음밥 먹었어", "김치볶음밥 먹었어"),
        )

        for answer, expected in corrections:
            with self.subTest(answer=answer):
                self.assertEqual(expected, handler._get_final_correction_segment(answer))
                self.assertEqual(
                    expected,
                    free_talk_generator._get_final_correction_segment(answer),
                )
                self.assertEqual(
                    expected,
                    recall_generator._get_final_correction_segment(answer),
                )
                self.assertEqual(
                    expected,
                    recall_score_calculator.get_final_correction_segment(answer),
                )

                with patch.object(
                    recall_score_calculator,
                    "calculate_similarity_score",
                    return_value=80.0,
                ) as similarity_mock:
                    result = recall_score_calculator.calculate_final_recall_score(
                        "김치볶음밥 먹었어",
                        answer,
                        ["김치볶음밥"],
                        "RECALL",
                    )

                self.assertEqual(
                    expected,
                    similarity_mock.call_args.kwargs["current_text"],
                )
                self.assertEqual(100.0, result["keywordScore"])


if __name__ == "__main__":
    unittest.main()
