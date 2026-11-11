"""Reusable inference for the D-drive app. No training or network calls."""
from pathlib import Path
from functools import lru_cache
import os, shutil, subprocess, tempfile
import numpy as np
import pandas as pd
import joblib
import soundfile as sf
from sklearn.svm import SVC
from .audio_features import extract_audio_features

def band_for_probability(p):
    if not np.isfinite(p) or not 0 <= p <= 1:
        raise ValueError('Invalid probability')
    return int(np.searchsorted([.2,.4,.6,.8],p,side='right')+1)

@lru_cache(maxsize=4)
def load_bundle(path):
    b=joblib.load(path)
    if 'model_files' in b:
        b['models']={n:joblib.load(Path(path).parent/f) for n,f in b['model_files'].items()}
    if any((len(model.feature_names_) if hasattr(model, 'feature_names_') else model.n_features_in_) != 40 for model, _ in b['models'].values()):
        raise ValueError('Every member must accept 40 features')
    if len(b['features'])!=40 or len(b['models'])!=4:
        raise ValueError('Expected original 40-feature, four-model bundle')
    return b

def predict_features(features, model_path=None):
    path=str(Path(model_path or os.getenv('MIDAS_ENSEMBLE_MODEL_PATH',Path(__file__).parent/'models/ensemble.joblib')).resolve())
    b=load_bundle(path)
    row=pd.DataFrame([features]).reindex(columns=b['features']).apply(pd.to_numeric,errors='coerce')
    if not np.isfinite(row.to_numpy()).all():
        raise ValueError('Missing or nonfinite required features')
    x=b['preprocessor'].transform(row)
    probs={}
    for name,(model,cal) in b['models'].items():
        if isinstance(model,SVC): z=model.decision_function(x)
        else:
            p=np.clip(model.predict_proba(x)[:,1],1e-6,1-1e-6)
            z=np.log(p/(1-p))
        probs[name]=float(cal.predict_proba(z.reshape(-1,1))[0,1])
    p=float(np.mean(list(probs.values())))
    return dict(abnormal_probability=p,predicted_label=int(p>=.5),
        reference_score=p*100,reference_band=band_for_probability(p),
        model_scores={n:v*100 for n,v in probs.items()},model_weights={n:.25 for n in probs},
        analysis_model='ensemble40',score_interpretation='dataset_reference_not_clinical_severity')

def extract_input_features(path):
    path=Path(path)
    if not path.is_file(): raise FileNotFoundError(str(path))
    ffmpeg=shutil.which(os.getenv('MIDAS_FFMPEG_PATH','ffmpeg'))
    with tempfile.TemporaryDirectory(prefix='midas_ensemble_') as temp:
        if path.suffix.lower() in {'.wav','.flac'}:
            decoded=path
        else:
            if not ffmpeg: raise RuntimeError('FFmpeg unavailable')
            decoded=Path(temp)/'input.wav'
            subprocess.run([ffmpeg,'-nostdin','-v','error','-y','-i',str(path),'-vn','-c:a','pcm_s16le',str(decoded)],check=True,capture_output=True,timeout=120)
        info=sf.info(decoded)
        if info.duration<.5: raise ValueError('Audio shorter than 0.5 seconds')
        peak=0.0
        with sf.SoundFile(decoded) as audio:
            for block in audio.blocks(blocksize=65536,dtype='float32'):
                peak=max(peak,float(np.max(np.abs(block))))
        if peak==0: raise ValueError('Silent audio')
        f=extract_audio_features(str(decoded))
        if not f or not f.get('egemaps_available'):
            raise ValueError('Audio/eGeMAPS feature extraction failed')
        return f
