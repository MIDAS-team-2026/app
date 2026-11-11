"""40-feature benchmark on the original sample/speaker splits; exploratory only."""
from pathlib import Path
import sys
import json
import time
import numpy as np
import pandas as pd
import joblib
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.linear_model import LogisticRegression
from sklearn.svm import SVC
from sklearn.metrics import f1_score, roc_auc_score, brier_score_loss

try:
    from .estimators import build_candidates, ARTIFACT_NAMES
except ImportError:
    from estimators import build_candidates, ARTIFACT_NAMES

ROOT = Path(__file__).resolve().parent
OLD = ROOT / 'results_30000_seed42'
OUT = ROOT / 'outputs'
FEATURES = [f'mfcc_{i}_{stat}' for i in range(1,14) for stat in ('mean','std')]
FEATURES += ['rms_mean','rms_std','zcr_mean','zcr_std','spectral_centroid_mean',
             'spectral_centroid_std','voiced_segments_per_sec','mean_unvoiced_segment_length',
             'mean_voiced_segment_length','voice_break_ratio','voice_activity_ratio',
             'pause_ratio','shimmer_local_db','jitter_local','hnr_db']
# Keep all original 15 features: 26 MFCC + 5 spectral/energy + 9 phonation/pause.
FEATURES.remove('spectral_centroid_std')
assert len(FEATURES) == 40

def score_bands(p):
    p = np.asarray(p, dtype=float)
    if not np.isfinite(p).all() or ((p < 0) | (p > 1)).any():
        raise ValueError('Probabilities must be finite and in [0,1]')
    return np.searchsorted([.2,.4,.6,.8], p, side='right') + 1

def weights(df):
    w = 1 / df.groupby('group')['group'].transform('size') / df.label.map(df.groupby('label')['group'].nunique())
    return (w / w.mean()).to_numpy()

def raw(model, x):
    if isinstance(model, SVC):
        return model.decision_function(x)
    p = np.clip(model.predict_proba(x)[:,1],1e-6,1-1e-6)
    return np.log(p/(1-p))

def metrics(y,p,w):
    return dict(macro_f1=float(f1_score(y,p>=.5,average='macro',sample_weight=w)),
                auc=float(roc_auc_score(y,p,sample_weight=w)),
                brier=float(brier_score_loss(y,p,sample_weight=w)))

def main():
    import argparse
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--elderly-csv', required=True)
    parser.add_argument('--dysarthria-csv', required=True)
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--output', default=str(OUT))
    args = parser.parse_args()
    OUT = Path(args.output)
    OUT.mkdir(parents=True, exist_ok=True)
    normal = pd.read_csv(args.elderly_csv,low_memory=False,dtype={'recorder_id':str})
    abnormal = pd.read_csv(args.dysarthria_csv,low_memory=False,dtype={'speaker_key':str})
    normal['sample_id'],normal['group'],normal['label'] = normal.audio_path,'elderly:'+normal.recorder_id,0
    abnormal['sample_id'],abnormal['group'],abnormal['label'] = abnormal.segment_id,'dysarthria:'+abnormal.speaker_key,1
    frame = pd.concat([normal,abnormal],ignore_index=True)
    manifest = pd.read_csv(args.manifest)
    frame = manifest[['sample_id','group','label','split']].merge(
        frame[['sample_id','group','label']+FEATURES],on=['sample_id','group','label'],validate='one_to_one',how='left',indicator=True)
    assert frame._merge.eq('both').all() and len(frame)==len(manifest)
    assert frame.groupby('group').split.nunique().max()==1
    assert set(frame.split)=={'fit','calibration','validation','test'}
    assert frame.groupby('split').label.nunique().eq(2).all()
    frame[FEATURES] = frame[FEATURES].apply(pd.to_numeric,errors='coerce').replace([np.inf,-np.inf],np.nan)
    parts = {s:frame[frame.split.eq(s)].copy() for s in ['fit','calibration','validation','test']}
    assert not parts['fit'][FEATURES].isna().all().any()
    prep = make_pipeline(SimpleImputer(strategy='median'),StandardScaler())
    prep.fit(parts['fit'][FEATURES])
    x = {s:prep.transform(d[FEATURES]) for s,d in parts.items()}
    candidates = build_candidates(parts['fit'].label)
    best,validation,timings = {},{},{}
    for name,models in candidates.items():
        top=-1
        start=time.perf_counter()
        for model in models:
            model.fit(x['fit'],parts['fit'].label,sample_weight=weights(parts['fit']))
            cal=LogisticRegression(C=1e6,max_iter=2000)
            cal.fit(raw(model,x['calibration']).reshape(-1,1),parts['calibration'].label,sample_weight=weights(parts['calibration']))
            p=cal.predict_proba(raw(model,x['validation']).reshape(-1,1))[:,1]
            value=metrics(parts['validation'].label,p,weights(parts['validation']))['macro_f1']
            if value>top:
                top=value; best[name]=(model,cal); validation[name]=p
        timings[name]=time.perf_counter()-start
        print(name,top,timings[name],flush=True)
    names=list(best)
    matrix=np.column_stack([validation[n] for n in names])
    # Fixed equal weights: no stacking trained on in-sample predictions.
    validation['Soft voting']=matrix.mean(axis=1)
    vm={n:metrics(parts['validation'].label,p,weights(parts['validation'])) for n,p in validation.items()}
    winner=max(vm,key=lambda n:vm[n]['macro_f1'])
    selection=dict(selected="Soft voting", best_validation_model=winner,validation=vm,ensemble_weights={n:1/len(names) for n in names})
    (OUT/'selection_before_test.json').write_text(json.dumps(selection,indent=2),encoding='utf-8')
    bundle=dict(features=FEATURES,preprocessor=prep,models=best,selection=selection,
                band_edges=[20,40,60,80],score_direction='higher = greater dysarthria-source similarity',
                interpretation='Binary source classification score bands; not five-class clinical severity')
    for name, pair in best.items():
        joblib.dump(pair, OUT/ARTIFACT_NAMES[name])
    bundle['model_files'] = ARTIFACT_NAMES
    del bundle['models']
    joblib.dump(bundle,OUT/'ensemble.joblib')
    # Verify saved inference, then evaluate final test once after selection is fixed.
    saved=joblib.load(OUT/'ensemble.joblib')
    saved['models']={n:joblib.load(OUT/f) for n,f in saved['model_files'].items()}
    sx=saved['preprocessor'].transform(parts['test'][FEATURES])
    pred=parts['test'][['sample_id','group','label']].copy()
    for name,(model,cal) in saved['models'].items():
        p=cal.predict_proba(raw(model,sx).reshape(-1,1))[:,1]
        original=best[name][1].predict_proba(raw(best[name][0],x['test']).reshape(-1,1))[:,1]
        np.testing.assert_allclose(p,original)
        pred[name]=p
    pred['Soft voting']=pred[names].mean(axis=1)
    rows=[]
    for name in validation:
        p=pred[name].to_numpy()
        rows.append(dict(model=name,**metrics(pred.label,p,weights(parts['test'])),seconds=timings.get(name,0)))
        pred[name+' score']=p*100
        pred[name+' band']=score_bands(p)
    pred.to_csv(OUT/'test_predictions.csv',index=False)
    pd.DataFrame(rows).to_csv(OUT/'metrics.csv',index=False)
    (OUT/'features.json').write_text(json.dumps(FEATURES,indent=2),encoding='utf-8')
    counts={n:pred[n+' band'].value_counts().reindex(range(1,6),fill_value=0).to_dict() for n in validation}
    (OUT/'band_counts.json').write_text(json.dumps(counts,indent=2),encoding='utf-8')
    print(pd.DataFrame(rows).to_string(index=False),flush=True)
    print('BANDS',counts,flush=True)
    print('DONE; selected:', 'Soft voting',flush=True)

if __name__=='__main__':
    np.testing.assert_array_equal(score_bands([0,.19999,.2,.4,.6,.8,1]),[1,1,2,3,4,5,5])
    main()
