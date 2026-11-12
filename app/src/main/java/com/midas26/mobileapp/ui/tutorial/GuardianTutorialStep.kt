
package com.midas26.mobileapp.ui.tutorial.guardian

/** 보호자 튜토리얼에서 현재 안내하는 화면. */
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

/** 튜토리얼에서 강조하는 하단 탐색 탭. */
enum class GuardianBottomTab {
    HOME,
    ANALYSIS,
    LOCATION,
    SETTINGS
}

/** 설정에서 다시 볼 튜토리얼의 범위. */
enum class GuardianTutorialReplayTarget {
    FULL,
    HOME,
    ANALYSIS,
    LOCATION,
    SETTINGS
}

/** 보호자 홈 화면의 안내 단계. */
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

/** 분석 대상 선택 및 분석 결과 화면의 안내 단계. */
enum class GuardianAnalysisTutorialStep {
    USER_SELECT,
    MAIN_SCORE,
    WEEKLY_GRAPH,
    DETAIL_SCORES,
    MOVE_TO_LOCATION_TAB,
    COMPLETED
}

/** 사용자 위치, 이동 경로 및 전화 기능의 안내 단계. */
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

/** 보호자 설정 화면의 안내 단계. */
enum class GuardianSettingsTutorialStep {
    ACCESSIBILITY,
    MANAGED_USERS,
    ANALYSIS_NOTIFICATION,
    REPLAY_TUTORIAL,
    COMPLETED
}
