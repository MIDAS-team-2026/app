"""Existing RF configuration, refitted on the 40-feature fit partition."""
import numpy as np
from sklearn.ensemble import RandomForestClassifier

def candidates(y):
    classes, counts = np.unique(y, return_counts=True)
    effective = (1 - .99) / (1 - np.power(.99, counts))
    class_weights = dict(zip(classes, effective / effective.sum() * len(classes)))
    return [RandomForestClassifier(n_estimators=200, max_depth=None,
        min_samples_leaf=1, max_features='sqrt', class_weight=class_weights,
        random_state=42, n_jobs=4)]
