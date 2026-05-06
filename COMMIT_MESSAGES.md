# 커밋 메시지 추천

지금까지 진행한 작업을 기준으로 한 커밋 메시지 추천입니다. Conventional Commits 컨벤션(`type: subject`)을 따르고 있고, 한글 제목 + 한글 본문으로 구성했습니다.

---

## 옵션 A — 작업 단위로 분리 (권장)

작업의 흐름을 명확하게 남기고 싶을 때 추천합니다. 3~4개의 커밋으로 분리합니다.

### 1. 인증 플로우 화면 추가

```
feat(auth): 스플래시·온보딩·로그인·회원가입 화면 추가

zip 으로 전달된 XML/Fragment 기반 인증 플로우를 Jetpack Compose
+ Navigation Compose 로 1:1 포팅했습니다.

추가된 화면
- SplashScreen: 1.8초 후 PrefsManager 상태에 따라 분기
- OnboardingScreen: HorizontalPager 기반 3단계 소개
- LoginScreen: 이메일/비밀번호 검증 + 회원가입 진입
- SignupRoleScreen: 사용자 / 보호자 역할 선택
- SignupInfoScreen: 이름·생년월일·이메일·비밀번호 입력

추가된 인프라
- Routes / MidasNavHost 로 nav_graph.xml 액션 1:1 매핑
- PrefsManager (com.midas26.mobileapp.util) 싱글톤 헬퍼 추가
- MidasPrimaryButton / MidasOutlineButton / MidasTextButton
- MidasOutlinedTextField (leading/trailing 아이콘, 비밀번호 토글)
- Material3 ColorScheme 에 Midas 브랜드 팔레트(Green/Gray/Red) 연결
- INTERNET, RECORD_AUDIO 권한 + windowSoftInputMode 추가
```

### 2. 컴파일 에러 수정 (OnboardingScreen by 위임)

```
fix(onboarding): animateDpAsState 위임을 위한 getValue import 누락 수정

`val width by animateDpAsState(...)` 위임에 필요한
androidx.compose.runtime.getValue import 가 OnboardingScreen.kt
에서 빠져 있어 다음 컴파일 오류가 발생했습니다.

  Type 'State<Dp>' has no method 'getValue(Nothing?, KProperty0<*>)',
  so it cannot serve as a delegate.

import 한 줄을 추가해 위임이 정상 동작하도록 수정합니다.
```

### 3. 노년층 친화 UI 리디자인

```
style(ui): 노년층 가독성을 위해 폰트·터치 영역·간격 일괄 확대

주 사용자층인 노년층의 가독성과 조작성을 우선해 디자인 토큰과
화면별 간격을 전반적으로 키웠습니다. 평균 폰트 +25~30%, 주요
터치 영역 +20% 수준으로 상향했습니다.

타이포그래피 (Type.kt)
- displaySmall   28→36sp
- headlineSmall  22→28sp
- titleLarge     18→22sp / titleMedium  16→20sp
- bodyLarge      15→19sp / bodyMedium   14→18sp
- bodySmall      13→16sp / labelLarge   15→18sp / labelMedium 13→16sp

컴포넌트
- 버튼: 높이 52→64dp, 코너 14→16dp
- 입력 필드: 코너 12→16dp, 아이콘 24→28dp
- 입력 라벨: bodyLarge(19sp) 로 확대

화면별
- Splash: 로고 96→120dp, 앱명 28→36sp, 인디케이터 28→36dp
- Onboarding: 일러스트 180→220dp, 인디케이터 점 8→12dp(활성 24→32dp)
- Login: 헤더 아이콘 56→72dp, 회원가입 안내 bodySmall→bodyMedium
- SignupRole: 역할 카드 패딩 20→24dp, 이모지 28→44sp, 체크 24→32dp
- SignupInfo: 진행 바 4→6dp, 뒤로가기 40→48dp, Spacer 전반 확대
```

---

## 옵션 B — 한 번에 묶기 (간단)

위 세 작업을 하나의 큰 커밋으로 정리하고 싶을 때 추천합니다.

```
feat(ui): 노년층 친화 인증 플로우 구현

zip 으로 전달된 XML/Fragment 인증 플로우를 Jetpack Compose 로
포팅하고, 주 사용자층인 노년층 가독성에 맞춰 폰트·터치 영역·
간격을 전반적으로 확대했습니다.

추가된 화면 (com.midas26.mobileapp.ui.*)
- onboarding/SplashScreen, OnboardingScreen
- auth/LoginScreen, SignupRoleScreen, SignupInfoScreen
- home/HomeScreen (자리표시자)

추가된 인프라
- navigation/Routes, MidasNavHost (nav_graph.xml 1:1 매핑)
- components/MidasButtons, MidasOutlinedTextField
- util/PrefsManager (싱글톤 헬퍼)
- Material3 ColorScheme 에 Midas 브랜드 팔레트 연결

노년층 친화 토큰
- 본문 14→18sp, 헤딩 22→28sp, 앱명 28→36sp
- 버튼 높이 52→64dp, 입력 코너 12→16dp
- 인디케이터 점 8→12dp, 일러스트 180→220dp

기타
- INTERNET, RECORD_AUDIO 권한 + windowSoftInputMode 추가
- AGP 의존성에 navigation-compose, material-icons-extended,
  lifecycle-viewmodel-compose 추가
- OnboardingScreen 에 누락되어 있던 androidx.compose.runtime.getValue
  import 추가 (animateDpAsState 위임 컴파일 오류 수정)
```

---

## 옵션 C — 한 줄 요약 (스쿼시 머지용)

PR 을 squash merge 할 때 또는 메인 브랜치 히스토리를 짧게 유지하고 싶을 때 추천합니다.

```
feat(ui): 노년층 친화 인증 플로우 추가 및 디자인 토큰 일괄 확대
```

또는

```
feat: 인증 플로우 화면 추가 + 노년층 친화 UI 리디자인
```

---

## 참고 — Conventional Commits 타입 가이드

| 타입 | 의미 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `style` | 코드 의미에 영향이 없는 스타일/포맷 변경 (UI 디자인 변경 포함) |
| `refactor` | 동작 변화 없는 구조 개선 |
| `chore` | 빌드, 의존성, 설정 변경 등 |
| `docs` | 문서 변경 |
| `test` | 테스트 추가/수정 |

UI 폰트 크기 변경처럼 "사용자 경험상 의미 있는 시각적 변경"은 `style:` 보다 `feat:` 또는 `refactor:` 로 두는 팀도 많습니다. 프로젝트 컨벤션에 맞춰 선택하시면 됩니다.
