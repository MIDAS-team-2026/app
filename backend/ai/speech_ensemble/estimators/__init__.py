from . import random_forest, logistic_regression, svm, catboost_model

MODULES = {'Random Forest': random_forest, 'Logistic Regression': logistic_regression,
           'RBF-SVM': svm, 'CatBoost': catboost_model}
ARTIFACT_NAMES = {'Random Forest': 'random_forest.joblib',
    'Logistic Regression': 'logistic_regression.joblib', 'RBF-SVM': 'svm.joblib',
    'CatBoost': 'catboost.joblib'}

def build_candidates(y):
    return {name: module.candidates(y) for name, module in MODULES.items()}
