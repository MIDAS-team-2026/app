from catboost import CatBoostClassifier

def candidates(y):
    return [CatBoostClassifier(depth=d, iterations=400, learning_rate=.05,
        thread_count=4, random_seed=42, verbose=False, allow_writing_files=False)
        for d in [4, 6]]
