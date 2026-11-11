package com.midas26.mobileapp.ui.tutorial

enum class TutorialScreen {
    HOME,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS,
    COMPLETED
}

enum class TutorialBottomTab {
    HOME,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS
}

enum class HomeTutorialStep {
    MOVE_TO_HOME_TAB,
    WELCOME,
    WEEKLY_CHECK,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS,
    MOVE_TO_VOICE_TAB,
    COMPLETED
}

enum class VoiceChatTutorialStep {
    MESSAGE,
    MICROPHONE,
    END_BUTTON,
    MOVE_TO_ANALYSIS_TAB,
    COMPLETED
}

enum class AnalysisTutorialStep {
    MAIN_SCORE,
    WEEKLY_GRAPH,
    DETAIL_SCORES,
    MOVE_TO_SETTINGS_TAB,
    COMPLETED
}

enum class SettingsTutorialStep {
    ACCESSIBILITY,
    LOCATION_SHARING,
    CHECK_NOTIFICATION,
    NOTIFICATION_TIME,
    /**
     * 전체 튜토리얼 마지막에 앱 사용법 다시 보기 메뉴를 안내합니다.
     */
    REPLAY_TUTORIAL,

    COMPLETED
}