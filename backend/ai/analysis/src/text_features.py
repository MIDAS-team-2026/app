import re
import pandas as pd


def clean_text(text):
    if pd.isna(text):
        return ""

    text = str(text).strip()
    text = re.sub(r"\s+", " ", text)
    return text


def tokenize_simple(text):
    text = clean_text(text)
    text = re.sub(r"[^\w\s가-힣]", "", text)
    return text.split()


def extract_text_features(text):
    text = clean_text(text)
    tokens = tokenize_simple(text)

    char_count = len(text.replace(" ", ""))
    word_count = len(tokens)
    unique_word_count = len(set(tokens))

    if word_count > 0:
        lexical_diversity = unique_word_count / word_count
        repetition_ratio = 1 - lexical_diversity
        avg_word_length = sum(len(token) for token in tokens) / word_count
    else:
        lexical_diversity = 0
        repetition_ratio = 0
        avg_word_length = 0

    short_answer_flag = 1 if word_count <= 5 else 0

    return {
        "char_count": char_count,
        "word_count": word_count,
        "unique_word_count": unique_word_count,
        "lexical_diversity": round(lexical_diversity, 3),
        "repetition_ratio": round(repetition_ratio, 3),
        "avg_word_length": round(avg_word_length, 3),
        "short_answer_flag": short_answer_flag
    }


def calculate_basic_speech_features(text_features, record_time):
    try:
        record_time = float(record_time)
    except:
        record_time = 0

    word_count = text_features.get("word_count", 0)
    char_count = text_features.get("char_count", 0)

    if record_time > 0:
        speech_rate_word = word_count / record_time
        speech_rate_char = char_count / record_time
    else:
        speech_rate_word = 0
        speech_rate_char = 0

    slow_speech_flag = 1 if speech_rate_word < 0.9 else 0
    long_recording_flag = 1 if record_time > 7.3 else 0
    low_content_slow_speech_flag = 1 if word_count <= 6 and record_time > 7.3 else 0

    return {
        "record_time_float": record_time,
        "speech_rate_word": round(speech_rate_word, 3),
        "speech_rate_char": round(speech_rate_char, 3),
        "slow_speech_flag": slow_speech_flag,
        "long_recording_flag": long_recording_flag,
        "low_content_slow_speech_flag": low_content_slow_speech_flag
    }