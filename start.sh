#!/bin/bash
# MIDAS 로컬 개발 서버 실행 스크립트
#   - Python AI 서버 (포트 8000)
#   - Spring 백엔드   (포트 8080)
# 두 서버를 함께 띄운다. Ctrl+C 로 종료하면 Python 서버도 함께 정리된다.

set -u

# 스크립트가 놓인 위치를 프로젝트 루트로 잡는다.
# (경로를 직접 적어 두지 않으므로 팀원 누구의 PC에서든 그대로 동작한다)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AI_DIR="$ROOT/backend/ai"

# --- 환경변수 로드 ---------------------------------------------------
# backend/.env 가 있으면 읽어서 두 서버에 전달한다. (양식: backend/.env.example)
ENV_FILE="$ROOT/backend/.env"
if [ -f "$ENV_FILE" ]; then
  echo "[env] backend/.env 로드"
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
else
  echo "[env] backend/.env 가 없습니다. 이미 설정된 시스템 환경변수를 그대로 사용합니다."
  echo "      (backend/.env.example 을 backend/.env 로 복사해 값을 채우면 자동으로 읽습니다)"
fi

# --- Python 실행 파일 찾기 -------------------------------------------
# 우선순위: 프로젝트 가상환경 > py 런처(3.11) > 시스템 python
if [ -x "$AI_DIR/.venv/Scripts/python.exe" ]; then
  PY="$AI_DIR/.venv/Scripts/python.exe"          # Windows 가상환경
elif [ -x "$AI_DIR/.venv/bin/python" ]; then
  PY="$AI_DIR/.venv/bin/python"                  # macOS / Linux 가상환경
elif command -v py >/dev/null 2>&1; then
  PY="py -3.11"
else
  PY="python"
fi

# --- Python AI 서버 (백그라운드) --------------------------------------
echo "[ai]  Python AI 서버 시작 (포트 8000)"
echo "[ai]  사용 인터프리터: $PY"
cd "$AI_DIR"
# NumPy 2.x와 TensorFlow 충돌 방지 (STT는 PyTorch 사용)
export USE_TF=0
$PY -m uvicorn fastapi_ai:app --host 0.0.0.0 --port 8000 &
PYTHON_PID=$!
echo "[ai]  PID: $PYTHON_PID"

# 어떤 이유로 종료되든 Python 서버를 함께 정리한다.
cleanup() {
  if kill -0 "$PYTHON_PID" 2>/dev/null; then
    kill "$PYTHON_PID" 2>/dev/null
    echo "[ai]  Python 서버 종료됨."
  fi
}
trap cleanup EXIT INT TERM

# --- Spring 백엔드 (포그라운드) ---------------------------------------
echo "[web] Spring 백엔드 시작 (포트 8080)"
cd "$ROOT/backend"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) MVN="./mvnw.cmd" ;;   # Windows (Git Bash 등)
  *)                    MVN="./mvnw"     ;;   # macOS / Linux
esac
"$MVN" spring-boot:run
