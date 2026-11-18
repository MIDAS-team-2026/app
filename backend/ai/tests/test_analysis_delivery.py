import importlib.util
import sys
import types
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch


AI_ROOT = Path(__file__).resolve().parents[1]


def _load_main_module():
    user_turn_stub = types.ModuleType("user_turn_analysis")
    user_turn_stub.analyze_user_turn = Mock()

    session_summary_stub = types.ModuleType("session_speech_summary")
    session_summary_stub.summarize_session_speech = Mock()

    try:
        import requests as requests_module
    except ImportError:
        requests_module = types.ModuleType("requests")

        class RequestError(Exception):
            def __init__(self, message="", response=None):
                super().__init__(message)
                self.response = response

        requests_module.HTTPError = RequestError
        requests_module.Timeout = RequestError
        requests_module.ConnectionError = RequestError
        requests_module.post = Mock()

    spec = importlib.util.spec_from_file_location(
        "main_under_test",
        AI_ROOT / "main.py",
    )
    module = importlib.util.module_from_spec(spec)

    with patch.dict(
        sys.modules,
        {
            "requests": requests_module,
            "user_turn_analysis": user_turn_stub,
            "session_speech_summary": session_summary_stub,
        },
    ):
        spec.loader.exec_module(module)

    return module


main = _load_main_module()


class FakeResponse:
    def __init__(self, status_code):
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            error = main.requests.HTTPError(
                f"HTTP {self.status_code}",
            )
            error.response = self
            raise error


class AnalysisDeliveryTest(unittest.TestCase):
    def test_retries_retryable_server_error_then_succeeds(self):
        with (
            patch.object(
                main.requests,
                "post",
                side_effect=[FakeResponse(503), FakeResponse(200)],
            ) as post_mock,
            patch.object(main.time, "sleep") as sleep_mock,
        ):
            main.send_analysis_to_spring(
                {"recordAnalysis": {"recordId": 1}}
            )

        self.assertEqual(2, post_mock.call_count)
        sleep_mock.assert_called_once_with(main.SPRING_RETRY_BACKOFF_SECONDS)

    def test_does_not_retry_non_retryable_client_error(self):
        with (
            patch.object(
                main.requests,
                "post",
                return_value=FakeResponse(400),
            ) as post_mock,
            patch.object(main.time, "sleep") as sleep_mock,
        ):
            with self.assertRaises(main.requests.HTTPError):
                main.send_analysis_to_spring(
                    {"recordAnalysis": {"recordId": 1}}
                )

        post_mock.assert_called_once()
        sleep_mock.assert_not_called()

    def test_raises_after_retry_limit_is_exhausted(self):
        with (
            patch.object(
                main.requests,
                "post",
                side_effect=[
                    FakeResponse(503),
                    FakeResponse(503),
                    FakeResponse(503),
                ],
            ) as post_mock,
            patch.object(main.time, "sleep") as sleep_mock,
        ):
            with self.assertRaises(main.requests.HTTPError):
                main.send_analysis_to_spring(
                    {"recordAnalysis": {"recordId": 1}}
                )

        self.assertEqual(main.SPRING_MAX_ATTEMPTS, post_mock.call_count)
        self.assertEqual(main.SPRING_MAX_ATTEMPTS - 1, sleep_mock.call_count)

    def test_audio_failure_raises_without_sending_to_spring(self):
        analysis_result = {
            "recordId": 1,
            "sessionId": 2,
            "transcriptText": "오늘 산책했어요",
            "audio_analysis_available": False,
            "audio_analysis_error": "audio unavailable",
            "word_count": 2,
            "lexical_diversity": 1.0,
            "repetition_ratio": 0.0,
            "slow_speech_flag": 0,
            "long_recording_flag": 0,
            "low_content_slow_speech_flag": 0,
        }
        args = SimpleNamespace(
            record_id=1,
            session_id=2,
            audio_path="",
            audio_url="",
            transcript_text="오늘 산책했어요",
            duration_sec=2.0,
        )

        with (
            patch.object(main, "analyze_user_turn", return_value=analysis_result),
            patch.object(main, "send_analysis_to_spring") as send_mock,
        ):
            with self.assertRaisesRegex(RuntimeError, "오디오 분석 실패"):
                main.run_record_mode(args)

        send_mock.assert_not_called()


if __name__ == "__main__":
    unittest.main()
