package com.midas26.mobileapp.ui.tutorial.guardian

enum class GuardianTutorialScreen {
    HOME,
    ANALYSIS_SELECT,
    ANALYSIS_RESULT,
    LOCATION_SELECT,
    LOCATION_DETAIL,
    LOCATION_ROUTE,
    SETTINGS,
    COMPLETED
}

enum class GuardianBottomTab {
    HOME,
    ANALYSIS,
    LOCATION,
    SETTINGS
}

enum class GuardianTutorialReplayTarget {
    FULL,
    HOME,
    ANALYSIS,
    LOCATION,
    SETTINGS
}

enum class GuardianHomeTutorialStep {
    MOVE_TO_HOME_TAB,
    LINKED_USER,
    TODAY_SCORE,
    ANALYSIS_MENU,
    LOCATION_MENU,
    SETTINGS_MENU,
    MOVE_TO_ANALYSIS_TAB,
    COMPLETED
}

enum class GuardianAnalysisTutorialStep {
    USER_SELECT,
    MAIN_SCORE,
    WEEKLY_GRAPH,
    DETAIL_SCORES,
    MOVE_TO_LOCATION_TAB,
    COMPLETED
}

enum class GuardianLocationTutorialStep {
    USER_SELECT,
    CURRENT_LOCATION,
    ROUTE_BUTTON,
    ROUTE_RESULT,
    CALL_BUTTON,
    CALL_DIALOG,
    MOVE_TO_SETTINGS_TAB,
    COMPLETED
}

enum class GuardianSettingsTutorialStep {
    ACCESSIBILITY,
    MANAGED_USERS,
    ANALYSIS_NOTIFICATION,
    REPLAY_TUTORIAL,
    COMPLETED
}