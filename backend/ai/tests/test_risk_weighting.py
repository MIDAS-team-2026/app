import sys
import types
import unittest
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_ROOT))


# 위험도 산식 테스트에서는 임베딩 모델과 형태소 분석기가 필요하지 않다.
# 무거운 외부 모델을 로드하지 않도록 최소 모듈만 대체한다.
sentence_transformers_stub = types.ModuleType("sentence_transformers")
sentence_transformers_stub.SentenceTransformer = lambda *_args, **_kwargs: object()
sentence_transformers_stub.util = types.SimpleNamespace()
sys.modules.setdefault("sentence_transformers", sentence_transformers_stub)
sys.modules.setdefault("text_linguistic_markers", types.ModuleType("text_linguistic_markers"))

if "requests" not in sys.modules:
    try:
        import requests  # noqa: F401
    except ImportError:
        sys.modules["requests"] = types.ModuleType("requests")


from recall import recall_api_client
from recall import recall_score_calculator


class RiskWeightingTest(unittest.TestCase):
    def test_weights_are_balanced_and_sum_to_one(self):
        self.assertEqual(0.5, recall_score_calculator.SPEECH_RISK_WEIGHT)
        self.assertEqual(0.5, recall_score_calculator.COGNITIVE_RISK_WEIGHT)
        self.assertEqual(
            1.0,
            recall_score_calculator.SPEECH_RISK_WEIGHT
            + recall_score_calculator.COGNITIVE_RISK_WEIGHT,
        )

    def test_recall_risk_uses_equal_speech_and_cognitive_weights(self):
        result = recall_score_calculator.calculate_final_risk_score(
            speech_risk_score=80.0,
            recall_score=60.0,
        )

        self.assertEqual(60.0, result["finalRiskScore"])
        self.assertEqual("HIGH", result["riskLevel"])

    def test_operating_text_score_path_uses_the_same_weights(self):
        result = recall_api_client.calculate_final_risk_from_text_score(
            speech_risk_score=80.0,
            text_score=60.0,
        )

        self.assertEqual(60.0, result["finalRiskScore"])
        self.assertEqual("HIGH", result["riskLevel"])


if __name__ == "__main__":
    unittest.main()
