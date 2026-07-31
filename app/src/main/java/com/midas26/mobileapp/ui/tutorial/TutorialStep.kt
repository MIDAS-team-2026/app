package com.midas26.mobileapp.ui.tutorial

/**
 * 튜토리얼이 진행되는 화면을 구분합니다.
 */
enum class TutorialScreen {
    HOME,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS,
    COMPLETED
}

/**
 * 홈 화면의 튜토리얼 단계입니다.
 */
enum class HomeTutorialStep {
    WELCOME,
    WEEKLY_CHECK,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS,
    COMPLETED
}

/**
 * 음성 대화 화면의 튜토리얼 단계입니다.
 */
enum class VoiceChatTutorialStep {
    MESSAGE,
    MICROPHONE,
    END_BUTTON,
    COMPLETED
}

/**
 * 분석 결과 화면의 튜토리얼 단계입니다.
 */
enum class AnalysisTutorialStep {
    MAIN_SCORE,
    WEEKLY_GRAPH,
    DETAIL_SCORES,
    COMPLETED
}

/**
 * 설정 화면의 튜토리얼 단계입니다.
 */
enum class SettingsTutorialStep {
    ACCESSIBILITY,
    LOCATION_SHARING,
    CHECK_NOTIFICATION,
    NOTIFICATION_TIME,
    SUPPORT,
    COMPLETED
}