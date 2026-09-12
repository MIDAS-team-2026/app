"""Local integration checks; never sends analysis to Spring or an external service."""
from pathlib import Path
import sys,os,json,tempfile
from unittest.mock import patch
import numpy as np
import pandas as pd
import soundfile as sf
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
sys.path.insert(0,str(ROOT/'analysis/src'))
from speech_ensemble.runtime import predict_features,extract_input_features,band_for_probability
import user_turn_analysis as analysis
import main as api

def main():
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument('--features',required=True)
    parser.add_argument('--output',required=True)
    args=parser.parse_args()
    out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    df=pd.read_csv(args.features)
    legacy,prep=analysis.load_speech_abnormality_reference()
    rows=[]
    for _,row in df.iterrows():
        f=row.to_dict(); new=predict_features(f)
        error=None
        try: old=analysis.predict_from_feature_dict(f,legacy,prep)['abnormal_probability']*100
        except Exception as exc: old=None;error=str(exc)
        rows.append(dict(file_name=row.file_name,legacy_rf_score=old,ensemble_score=new['reference_score'],
                         ensemble_band=new['reference_band'],legacy_error=error))
    pd.DataFrame(rows).to_csv(out/'comparison.csv',index=False,encoding='utf-8-sig')
    checks=[]
    assert [band_for_probability(p) for p in [0,.2,.4,.6,.8,1]]==[1,2,3,4,5,5]
    checks.append('0/20/40/60/80/100 band boundaries')
    for name in ['test1.m4a','test5.m4a']:
        row=df[df.file_name.eq(name)].iloc[0]
        os.environ['MIDAS_SPEECH_MODEL']='ensemble40'
        result=analysis.analyze_user_turn_audio(row.source_path)
        assert result['audio_analysis_available'],result
        expected=predict_features(row.to_dict())
        np.testing.assert_allclose(result['reference_score'],expected['reference_score'],atol=1e-5)
        os.environ['MIDAS_SPEECH_MODEL']='legacy_rf'
        old=analysis.analyze_user_turn_audio(row.source_path)
        assert old['audio_analysis_available'],old
        assert old['analysis_model']=='ensemble40'
        np.testing.assert_allclose(old['reference_score'],expected['reference_score'],atol=1e-5)
        checks.append(name+': raw M4A extraction agrees with cached features; legacy selector cannot bypass ensemble')
    os.environ['MIDAS_SPEECH_MODEL']='ensemble40'
    with tempfile.TemporaryDirectory() as temp:
        for name,length in [('silence',16000),('short',100)]:
            wav=Path(temp)/(name+'.wav')
            sf.write(wav,np.zeros(length),16000)
            result=analysis.analyze_user_turn_audio(str(wav))
            assert not result['audio_analysis_available'] and result['reference_band'] is None
        result=analysis.analyze_user_turn_audio(str(Path(temp)/'missing.wav'))
        assert not result['audio_analysis_available'] and result['reference_score'] is None
    checks.append('silence, short, missing audio return unavailable/null reference score')
    with patch.object(api,'send_analysis_to_spring') as send:
        args=type('Args',(),dict(record_id=1,session_id=1,audio_path=df[df.file_name.eq('test1.m4a')].iloc[0].source_path,audio_url=None,transcript_text='테스트 문장',duration_sec=0))()
        response=api.run_record_mode(args)
        assert response['success'] and response['referenceAnalysis']['band']==5
        assert response['referenceAnalysis']['model']=='ensemble40'
        send.assert_called_once()
    checks.append('API handler returns new reference fields; Spring send mocked')
    summary=dict(checks=checks,files=len(rows),legacy_failures=sum(r['legacy_rf_score'] is None for r in rows),
                 means=pd.DataFrame(rows)[['legacy_rf_score','ensemble_score']].mean().to_dict(),
                 examples=[r for r in rows if r['file_name'] in ['test1.m4a','test5.m4a']])
    (out/'checks.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
