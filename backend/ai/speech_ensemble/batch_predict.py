"""여러 음성 파일(또는 폴더 안의 모든 음성 파일)을 앙상블로 점수 내고 CSV로 저장한다.

사용 예 (이 폴더에서 실행):
    python batch_predict.py 폴더경로
    python batch_predict.py 파일1.m4a 파일2.wav 폴더경로 --output result.csv
    python batch_predict.py 폴더경로 --no-recursive

폴더를 주면 하위 폴더까지 탐색하고, 첫 번째 하위 폴더 이름을 '그룹'으로 기록한다.
(예: 폴더경로/조용함/a.m4a -> 그룹 '조용함')
"""
import argparse
import csv
import os
import statistics
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("USE_TF", "0")  # NumPy 2.x와 TensorFlow 충돌 방지

from speech_ensemble.runtime import extract_input_features, predict_features  # noqa: E402

# 폴더 탐색 시 대상 확장자. 화면 녹화 등 영상(.mp4)이 섞이지 않도록 제외했다.
# (파일을 직접 지정하면 확장자와 상관없이 분석한다.)
AUDIO_EXTS = {".wav", ".m4a", ".mp3", ".flac", ".ogg", ".aac", ".wma"}
DEFAULT_MODEL = Path(__file__).resolve().parent / "models" / "ensemble.joblib"
PROB_CUTOFF_CAUTION, PROB_CUTOFF_ABNORMAL = 0.35, 0.65  # user_turn_analysis.py와 동일


def app_health_score(p):
    """앱 음성 건강점수 (user_turn_analysis.map_calibrated_prob_to_health_score와 같은 구간 매핑)."""
    if p < PROB_CUTOFF_CAUTION:
        return 100 - p / PROB_CUTOFF_CAUTION * 20
    if p < PROB_CUTOFF_ABNORMAL:
        return 79 - (p - PROB_CUTOFF_CAUTION) / (PROB_CUTOFF_ABNORMAL - PROB_CUTOFF_CAUTION) * 29
    return 49 - (p - PROB_CUTOFF_ABNORMAL) / (1 - PROB_CUTOFF_ABNORMAL) * 49


def collect(inputs, recursive):
    """(파일, 그룹) 목록을 만든다. 폴더는 하위 폴더 이름을 그룹으로 쓴다."""
    found = []
    for item in inputs:
        path = Path(item)
        if path.is_dir():
            pattern = "**/*" if recursive else "*"
            for f in sorted(path.glob(pattern)):
                if f.is_file() and f.suffix.lower() in AUDIO_EXTS:
                    rel = f.relative_to(path)
                    group = rel.parts[0] if len(rel.parts) > 1 else path.name
                    found.append((f, group))
        elif path.is_file():
            found.append((path, path.parent.name))
        else:
            print(f"[건너뜀] 없는 경로: {item}", file=sys.stderr)
    return found


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("inputs", nargs="+", help="음성 파일 또는 폴더 (여러 개 가능)")
    ap.add_argument("--model", default=str(DEFAULT_MODEL), help="ensemble.joblib 경로")
    ap.add_argument("--output", default="batch_result.csv", help="결과 CSV 경로")
    ap.add_argument("--no-recursive", action="store_true", help="폴더의 하위 폴더는 탐색하지 않음")
    args = ap.parse_args()

    targets = collect(args.inputs, recursive=not args.no_recursive)
    if not targets:
        sys.exit("분석할 음성 파일이 없습니다.")

    fields = ["group", "file", "ensemble", "band", "RF", "LR", "SVM", "CAT",
              "app_health", "duration_sec", "voice_ratio", "error"]
    rows = []
    for i, (f, group) in enumerate(targets, 1):
        print(f"[{i}/{len(targets)}] {f.name}", flush=True)
        try:
            feats = extract_input_features(str(f))
            r = predict_features(feats, args.model)
            m = r["model_scores"]
            rows.append(dict(
                group=group, file=f.name,
                ensemble=round(r["reference_score"], 1), band=r["reference_band"],
                RF=round(m["Random Forest"], 1), LR=round(m["Logistic Regression"], 1),
                SVM=round(m["RBF-SVM"], 1), CAT=round(m["CatBoost"], 1),
                app_health=round(app_health_score(r["abnormal_probability"]), 1),
                duration_sec=round(feats.get("audio_duration", 0), 1),
                voice_ratio=round(feats.get("voice_activity_ratio", 0), 2)))
        except Exception as e:  # 한 파일이 실패해도 나머지는 계속 진행
            rows.append(dict(group=group, file=f.name, error=str(e)[:100]))

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", newline="", encoding="utf-8-sig") as fp:  # 엑셀에서 한글이 깨지지 않도록 BOM 포함
        w = csv.DictWriter(fp, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)

    ok = [r for r in rows if "ensemble" in r]
    print(f"\n완료: {len(ok)}개 성공, {len(rows) - len(ok)}개 실패 -> {out.resolve()}")
    groups = {}
    for r in ok:
        groups.setdefault(r["group"], []).append(r["ensemble"])
    if len(groups) > 0:
        print("\n그룹별 앙상블 점수 (개수 / 평균 / 최소~최대)")
        for g, v in groups.items():
            print(f"  {g}: {len(v)}개 / {statistics.mean(v):.1f} / {min(v):.1f}~{max(v):.1f}")


if __name__ == "__main__":
    main()
