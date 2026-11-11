from sklearn.svm import SVC

def candidates(y):
    return [SVC(C=c, cache_size=1024) for c in [1, 10]]
