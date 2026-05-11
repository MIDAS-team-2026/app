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
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Login = "login"
    const val SignupRole = "signup_role"

    // Signup info: role 을 path arg 로 전달
    private const val SignupInfoBase = "signup_info"
    const val SignupInfoArgRole = "role"
    const val SignupInfo = "$SignupInfoBase/{$SignupInfoArgRole}"
    fun signupInfo(role: String) = "$SignupInfoBase/$role"

    // 인증 직후 흐름 — role 인자를 path 로 전달
    const val PermissionArgRole = "role"
    const val Permission = "permission/{$PermissionArgRole}"
    fun permission(role: String) = "permission/$role"

    const val PrivacyArgRole = "role"
    const val Privacy = "privacy/{$PrivacyArgRole}"
    fun privacy(role: String) = "privacy/$role"

    const val SignupComplete = "signup_complete"

    // 메인 홈
    const val UserHome = "user_home"
    const val GuardianHome = "guardian_home"

    // 음성 대화
    const val VoiceChat = "voice_chat"
    const val VoiceChatDisconnected = "voice_chat_disconnected"

    // 회상 과제
    const val RecallStart = "recall_start"
    const val RecallQuestion = "recall_question"
    const val RecallResult = "recall_result"

    // 분석
    const val AnalysisLoading = "analysis_loading"
    const val AnalysisResult = "analysis_result"
    const val AnalysisGraph = "analysis_graph"

    // 설정
    const val Settings = "settings"

    /** 호환용 별칭. 사용자 역할에 맞는 홈으로 분기할 때 사용. */
    const val Home = UserHome
}
