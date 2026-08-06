"""
자유대화 텍스트에서 언어적 인지 지표를 계산한다.

Fraser et al. (2015), "Linguistic Features Identify Alzheimer's Disease in
Narrative Speech"에서 진단과 상관관계가 검증된 지표 중, 오디오 없이 텍스트만으로
계산 가능한 것들을 kiwipiepy 형태소 분석 기반으로 구현했다.

- pronoun_noun_ratio: 대명사:명사 비율. 해당 논문에서 진단과 가장 강하게
  상관된 단일 지표(r=0.35)였다. 구체적인 명사 대신 대명사로 얼버무리는
  경향("그거", "저거")을 잡아낸다.
- noun_ratio: 전체 내용어 중 명사 비율. 의미적으로 빈곤한 발화일수록 낮다.
- lexical_diversity_mattr: 텍스트 길이에 안정적인 어휘 다양성(MATTR).
- repetition_score: 세션 내 발화 간 평균 코사인 유사도. 높을수록 같은 내용을
  반복하는 경향(perseveration)이 강하다는 뜻이다.

이 모듈은 오디오/음성 분석 파이프라인(librosa 등)과 완전히 무관하게
텍스트만으로 동작한다. 값을 위험도 점수로 환산하는 로직은 포함하지 않는다 —
이 논문의 요인분석 가중치는 영어 DementiaBank 데이터 기준이라 한국어 데이터에
그대로 옮겨 쓸 수 없고, 별도의 보정이 필요하다.

대신 compute_baseline_deviation()으로 "전체 정상 범위"가 아니라 "이 사용자
본인의 최근 기준선 대비 오늘이 얼마나 벗어났는지"를 본다. 사용자 수가 아직
적어 전체 인구 기준 정상 범위를 통계적으로 잡을 근거가 없는 지금 단계에서는,
개인별 추이 모니터링이 더 현실적이다.
"""

import math
from collections import Counter
from statistics import mean, stdev

from kiwipiepy import Kiwi

# 값이 어느 방향으로 움직일 때 인지 저하 신호로 보는지(참고용 메타데이터).
# "increase": 값이 커질수록 우려스러움, "decrease": 값이 작아질수록 우려스러움.
CONCERNING_DIRECTION = {
    "pronounNounRatio": "increase",
    "nounRatio": "decrease",
    "lexicalDiversityMattr": "decrease",
    "repetitionScore": "increase",
}

MIN_BASELINE_SESSIONS = 3

_kiwi = Kiwi()

_NOUN_TAGS = {"NNG", "NNP"}
_PRONOUN_TAGS = {"NP"}
_CONTENT_TAGS = {
    "NNG", "NNP", "NNB", "NR", "NP",
    "VV", "VA", "VX", "XSA", "XSV",
    "MAG", "XR",
}


def _tokens(text: str) -> list:
    return list(_kiwi.tokenize(str(text or "")))


def pronoun_noun_ratio(texts: list[str]) -> float | None:
    """대명사 수 / 명사 수. 명사가 하나도 없으면 계산 불가(None)."""
    pronoun_count = 0
    noun_count = 0

    for text in texts:
        for token in _tokens(text):
            if token.tag in _PRONOUN_TAGS:
                pronoun_count += 1
            elif token.tag in _NOUN_TAGS:
                noun_count += 1

    if noun_count == 0:
        return None

    return pronoun_count / noun_count


def noun_ratio(texts: list[str]) -> float | None:
    """전체 내용어(명사/동사/형용사/부사/어근) 중 명사 비율."""
    noun_count = 0
    content_count = 0

    for text in texts:
        for token in _tokens(text):
            if token.tag in _CONTENT_TAGS:
                content_count += 1

                if token.tag in _NOUN_TAGS:
                    noun_count += 1

    if content_count == 0:
        return None

    return noun_count / content_count


def lexical_diversity_mattr(texts: list[str], window_size: int = 10) -> float | None:
    """MATTR(Moving-Average Type-Token Ratio).

    내용어 형태소 단위로 계산해서, 조사·어미 차이 때문에 같은 단어가
    다른 단어로 세이는 것을 방지한다. 전체 토큰 수가 window_size보다
    적으면 단순 type-token ratio로 대체한다.
    """
    forms = [
        token.form
        for text in texts
        for token in _tokens(text)
        if token.tag in _CONTENT_TAGS
    ]

    if not forms:
        return None

    if len(forms) < window_size:
        return len(set(forms)) / len(forms)

    window_count = len(forms) - window_size + 1
    ratio_sum = 0.0

    for start in range(window_count):
        window = forms[start:start + window_size]
        ratio_sum += len(set(window)) / window_size

    return ratio_sum / window_count


def repetition_score(texts: list[str]) -> float | None:
    """세션 내 발화들 간 평균 코사인 유사도(내용어 bag-of-words 기준).

    값이 높을수록 발화끼리 같은 단어를 반복해서 쓴다는 뜻이다.
    비교할 발화가 2개 미만이면 None을 반환한다.
    """
    bags = [
        Counter(token.form for token in _tokens(text) if token.tag in _CONTENT_TAGS)
        for text in texts
    ]
    bags = [bag for bag in bags if bag]

    if len(bags) < 2:
        return None

    similarities = [
        _cosine_similarity(bags[i], bags[j])
        for i in range(len(bags))
        for j in range(i + 1, len(bags))
    ]

    return sum(similarities) / len(similarities)


def _cosine_similarity(a: Counter, b: Counter) -> float:
    shared_keys = set(a) & set(b)
    dot_product = sum(a[key] * b[key] for key in shared_keys)
    norm_a = math.sqrt(sum(value * value for value in a.values()))
    norm_b = math.sqrt(sum(value * value for value in b.values()))

    if norm_a == 0 or norm_b == 0:
        return 0.0

    return dot_product / (norm_a * norm_b)


def compute_baseline_deviation(
    history_values: list[float | None],
    current_value: float | None,
) -> dict:
    """current_value가 이 사용자 본인의 과거 이력 대비 얼마나 벗어났는지 계산한다.

    전체 인구 기준 "정상 범위" 대신, 같은 사용자의 최근 세션들(history_values)
    평균/표준편차를 기준선으로 삼는다. 표본이 MIN_BASELINE_SESSIONS(3)보다
    적으면 z-score는 불안정하므로 계산하지 않고 None을 반환한다 — 기준선이
    아직 안 쌓인 신규 사용자를 "정상"으로 오판하지 않기 위함이다.

    반환값의 zScore는 부호가 그대로 방향을 담고 있다(양수 = 기준선보다 큼).
    어느 방향이 우려스러운 신호인지는 CONCERNING_DIRECTION을 참고해서
    호출하는 쪽에서 해석한다 — 지표마다 "커지면 나쁨"/"작아지면 나쁨"이
    다르기 때문이다.
    """
    valid_history = [value for value in history_values if value is not None]
    baseline_mean = mean(valid_history) if valid_history else None

    result = {
        "baselineMean": baseline_mean,
        "baselineSampleSize": len(valid_history),
        "zScore": None,
    }

    if current_value is None or len(valid_history) < MIN_BASELINE_SESSIONS:
        return result

    baseline_std = stdev(valid_history)

    if baseline_std == 0:
        result["zScore"] = 0.0 if current_value == baseline_mean else None
        return result

    result["zScore"] = (current_value - baseline_mean) / baseline_std
    return result
