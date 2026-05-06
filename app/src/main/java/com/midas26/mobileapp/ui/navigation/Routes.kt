package com.midas26.mobileapp.ui.navigation

/**
 * Navigation Compose 라우트 상수.
 *
 * zip 파일의 nav_graph.xml 액션을 1:1로 옮긴 형태로,
 * `signup_info` 라우트만 role 인자를 받도록 구성합니다.
 */
object Routes {
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Login = "login"
    const val SignupRole = "signup_role"

    // Signup info: role을 path arg로 전달
    private const val SignupInfoBase = "signup_info"
    const val SignupInfoArgRole = "role"
    const val SignupInfo = "$SignupInfoBase/{$SignupInfoArgRole}"
    fun signupInfo(role: String) = "$SignupInfoBase/$role"

    /** 후속 단계에서 구현될 홈. 현재는 자리표시자. */
    const val Home = "home"
}
