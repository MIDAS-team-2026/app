package com.midas26.mobileapp.ui.navigation

/**
 * Navigation Compose 라우트 상수.
 *
 * 흐름:
 *   Splash → (Onboarding) → Login
 *   Login → SignupRole → SignupInfo/{role} → Permission → Privacy
 *           → (사용자) SignupComplete → UserHome
 *           → (보호자)                     GuardianHome
 *   Login (이미 가입됨) → 역할에 따라 UserHome / GuardianHome
 */
object Routes {
    const val Splash               = "splash"
    const val Onboarding           = "onboarding"
    const val OnboardingPermission = "onboarding_permission"
    const val Login                = "login"
    const val LoginForm            = "login_form"
    const val SignupRole  = "signup_role"

    // Signup info: role 을 path arg 로 전달
    private const val SignupInfoBase = "signup_info"
    const val SignupInfoArgRole      = "role"
    const val SignupInfo             = "$SignupInfoBase/{$SignupInfoArgRole}"
    fun signupInfo(role: String)     = "$SignupInfoBase/$role"

    // 전화번호 인증 — phone + role 을 path arg 로 전달
    const val PhoneVerificationArgPhone = "phone"
    const val PhoneVerificationArgRole  = "role"
    const val PhoneVerification         = "phone_verification/{$PhoneVerificationArgPhone}/{$PhoneVerificationArgRole}"
    fun phoneVerification(phone: String, role: String) = "phone_verification/$phone/$role"

    // 인증 직후 흐름 — role 인자를 path 로 전달
    const val PermissionArgRole = "role"
    const val Permission        = "permission/{$PermissionArgRole}"
    fun permission(role: String) = "permission/$role"

    const val PrivacyArgRole = "role"
    const val Privacy        = "privacy/{$PrivacyArgRole}"
    fun privacy(role: String) = "privacy/$role"

    const val SignupComplete = "signup_complete"

    // 메인 홈
    const val UserHome     = "user_home"
    const val GuardianHome = "guardian_home"

    // 음성 대화
    const val VoiceChat            = "voice_chat"
    const val VoiceChatDisconnected = "voice_chat_disconnected"

    // 회상 과제
    const val RecallStart    = "recall_start"
    const val RecallQuestion = "recall_question"
    const val RecallResult   = "recall_result"

    // 분석
    const val AnalysisResult  = "analysis_result"
    const val AnalysisGraph   = "analysis_graph"

    // 보호자 분석 - 사용자 선택
    const val AnalysisUserSelect = "analysis_user_select"

    // 설정
    const val Settings                      = "settings"
    const val AccessibilitySettings         = "accessibility_settings"
    const val LoginAccessibilitySettings    = "login_accessibility_settings"
    const val ProfileEdit           = "profile_edit"

    // 비밀번호 찾기
    const val ForgotPassword               = "forgot_password"
    const val ForgotPasswordVerifyArgPhone = "phone"
    const val ForgotPasswordVerify         = "forgot_password_verify/{$ForgotPasswordVerifyArgPhone}"
    fun forgotPasswordVerify(phone: String) = "forgot_password_verify/$phone"

    const val ResetPasswordArgPhone = "phone"
    const val ResetPassword         = "reset_password/{$ResetPasswordArgPhone}"
    fun resetPassword(phone: String) = "reset_password/$phone"

    // 회원탈퇴
    const val Withdraw               = "withdraw"
    const val WithdrawVerifyArgPhone = "phone"
    const val WithdrawVerify         = "withdraw_verify/{$WithdrawVerifyArgPhone}"
    fun withdrawVerify(phone: String) = "withdraw_verify/$phone"

    // ── 보호자 위치 정보 ──────────────────────────────────────────────────
    // 화면 1: 사용자 목록
    const val LocationList = "location_list"

    // 화면 2: 지도 + 이동경로/전화걸기 버튼 (userId를 path arg로 전달)
    const val LocationDetailArgUserId = "userId"
    const val LocationDetail          = "location_detail/{$LocationDetailArgUserId}"
    fun locationDetail(userId: String) = "location_detail/$userId"

    // 화면 3: 이동 경로 + 타임라인 (userId를 path arg로 전달)
    const val LocationRouteArgUserId = "userId"
    const val LocationRoute          = "location_route/{$LocationRouteArgUserId}"
    fun locationRoute(userId: String) = "location_route/$userId"

    /** 호환용 별칭 */
    const val Home = UserHome
}