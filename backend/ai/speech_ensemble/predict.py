"""CLI using the same inference implementation as the app."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from speech_ensemble.runtime import extract_input_features,predict_features

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model',required=True)
    p.add_argument('--audio',required=True)
    p.add_argument('--output',required=True)
    a=p.parse_args()
    result=predict_features(extract_input_features(a.audio),a.model)
    dest=Path(a.output);dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))

if __name__=='__main__':main()
