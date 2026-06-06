#!/bin/bash

ROOT="/c/Users/Admin/Desktop/CODE MASHED/MIDAS_2026/app"

# Python AI 서버 실행 (백그라운드)
echo "Starting Python AI Server (port 8000)..."
cd "$ROOT/backend/ai"
"/c/Users/Admin/AppData/Local/Programs/Python/Python311/Scripts/uvicorn.exe" fastapi_ai:app --host 0.0.0.0 --port 8000 &
PYTHON_PID=$!
echo "Python server PID: $PYTHON_PID"

# Spring 백엔드 실행
echo "Starting Spring Backend (port 8080)..."
cd "$ROOT/backend"
./mvnw.cmd spring-boot:run

# Spring 종료 시 Python 서버도 함께 종료
kill $PYTHON_PID
echo "Python server stopped."
