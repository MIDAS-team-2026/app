from sklearn.linear_model import LogisticRegression

def candidates(y):
    return [LogisticRegression(C=c, max_iter=2000, random_state=42) for c in [.1, 1]]
