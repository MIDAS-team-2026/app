# 똑똑 (Knock Knock)

음성 대화와 회상 과제로 인지 기능 변화를 살펴보는 안드로이드 앱입니다.
사용자가 앱과 대화하면 음성·언어·회상 결과를 분석해 점수로 보여 주고, 연결된 보호자가 결과와 위치를 확인할 수 있습니다.

> 분석 결과는 참고용 정보이며 의학적 진단이나 치료를 대체하지 않습니다.

영남대학교 컴퓨터공학과 캡스톤디자인 MIDAS 팀 프로젝트입니다.

## 구성

하나의 저장소에 앱, 백엔드, AI 서버가 함께 들어 있습니다.

| 폴더 | 내용 | 기술 |
|---|---|---|
| `app/` | 안드로이드 앱 | Kotlin, Jetpack Compose, Retrofit (minSdk 24, targetSdk 36) |
| `backend/` | API 서버 | Spring Boot 3.5, Java 17, JPA, MySQL, JWT, AWS S3 |
| `backend/ai/` | AI 서버 | Python, FastAPI |
| `docs/` | 설계 문서 | 요구사항(DRD), 설계(DSD), 백엔드–AI 연동 가이드 |

```text
앱 ──HTTP──▶ Spring 서버(8080) ──HTTP──▶ AI 서버(8000)
                 │  ▲                        │
                 ▼  └──── 분석 결과 저장 ◀───┘
              MySQL · S3
```

- 앱은 Spring 서버하고만 통신합니다.
- AI 서버는 DB에 직접 접근하지 않고, Spring 서버의 API를 호출해 결과를 저장합니다.
- 녹음한 음성 파일은 S3에 보관하고, 음성 인식과 질문 생성에는 외부 AI 서비스를 사용합니다.

## 주요 기능

- **음성 대화**: 앱이 질문하고 사용자가 말로 답합니다. 답변은 녹음해 서버로 보냅니다.
- **회상 질문**: 대화에서 한 말을 나중에 다시 물어보고, 날짜·요일 같은 기본 질문도 함께 묻습니다.
- **분석 결과**: 음성 점수와 기억력 점수, 종합 위험도를 날짜별·주간으로 보여 줍니다.
- **보호자 연동**: 보호자가 사용자 코드로 연결해 분석 결과를 확인합니다.
- **위치 공유**: 사용자가 설정에서 켠 경우에만 보호자가 현재 위치와 이동 경로를 볼 수 있습니다.
- **접근성**: 글자 크기, 고대비, 음성 안내 속도 등을 조절할 수 있습니다.

## 실행에 필요한 것

- JDK 17
- MySQL (데이터베이스 이름 `midas`)
- Python 3.11
- Android Studio (앱을 실행할 때)

### 환경변수

키와 비밀번호는 저장소에 올리지 않고 환경변수로 넣습니다. 값은 팀 내부에서 공유합니다.

| 이름 | 쓰는 곳 | 설명 |
|---|---|---|
| `DB_PASSWORD` | Spring | MySQL `root` 계정 비밀번호 |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | Spring | 음성 파일을 저장하는 S3 접근 키 |
| `JWT_SECRET` | Spring | 로그인 토큰 서명 키. 32바이트 이상의 임의 문자열을 직접 만들어 사용 (선택, 없으면 서버를 재시작할 때마다 다시 로그인 필요) |
| `AI_PYTHON_URL` | Spring | AI 서버 주소 (선택, 기본값 `http://localhost:8000`) |
| `GROQ_API_KEY` | AI 서버 | 음성 인식(Groq) 키 |
| `YNU_API_KEY` | AI 서버 | 질문 생성에 쓰는 LLM 게이트웨이 키 |
| `SPRING_BASE_URL` | AI 서버 | Spring 서버 주소 (선택, 기본값 `http://localhost:8080`) |

## 실행 방법

### 1. Spring 서버 (포트 8080)

```bash
cd backend
./mvnw spring-boot:run
```

Windows에서는 `mvnw.cmd spring-boot:run` 을 씁니다.
서버가 뜨면 `http://localhost:8080/swagger-ui/index.html` 에서 API 목록을 볼 수 있습니다.
테이블은 서버를 처음 띄울 때 자동으로 만들어집니다.

### 2. AI 서버 (포트 8000)

```bash
cd backend/ai
python -m venv .venv
```

가상환경을 켠 뒤 필요한 패키지를 설치합니다. 패키지 목록은 `analysis/`, `recall/`, `speech_ensemble/` 폴더의 `requirements.txt` 에 나뉘어 있습니다.

```bash
python -m uvicorn fastapi_ai:app --host 0.0.0.0 --port 8000
```

음성 분석 모델 파일은 용량 문제로 저장소에 올리지 않습니다. 팀 내부에서 받아 `backend/ai/speech_ensemble/models/` 에 넣어야 음성 분석이 동작합니다.

### 3. 앱

Android Studio에서 저장소 최상위 폴더를 열고 `app` 을 실행합니다.

앱의 서버 주소는 `app/src/main/java/com/midas26/mobileapp/network/RetrofitClient.kt` 의 `BASE_URL` 에 있습니다.
기본값 `http://10.0.2.2:8080/` 은 에뮬레이터에서 PC의 8080 포트를 가리키는 주소라서, 실제 기기에서 실행하려면 PC의 IP 주소로 바꿔야 합니다.

### 한 번에 실행하기

최상위 폴더의 `start.sh`(Windows는 `start.bat`)는 AI 서버와 Spring 서버를 함께 띄우는 스크립트입니다.
실행하기 전에 스크립트 안의 경로와 환경변수 설정을 자신의 PC에 맞게 확인하세요.

## 테스트

```bash
# 백엔드
cd backend
./mvnw test

# 앱 단위 테스트
./gradlew :app:testDebugUnitTest
```

AI 서버의 테스트는 `backend/ai/tests/` 에 있습니다.

`BackendApplicationTests` 는 실제 DB에 연결하므로 MySQL과 환경변수가 준비된 상태에서만 통과합니다.

## 문서

- [요구사항 정의서 (DRD)](docs/DRD_문서.md)
- [설계 문서 (DSD)](docs/DSD_문서.md)
- [백엔드–AI 연동 가이드](docs/ai_db_integrate_guide.md)
- [AI–DB 요구사항](docs/ai_db_requirements.md)
- AI 모듈별 설명: [음성 분석](backend/ai/analysis/README.md) · [회상 분석](backend/ai/recall/README.md) · [음성 앙상블 모델](backend/ai/speech_ensemble/README.md)

## 협업 방식

- 작업은 브랜치에서 하고 Pull Request로 `main` 에 병합합니다.
- 커밋 메시지는 `[추가]`, `[수정]`, `[삭제]` 같은 말머리로 시작합니다.
