
package com.midas26.mobileapp.ui.tutorial.guardian

/** 보호자 튜토리얼의 전체 진행 상태와 화면별 다시 보기 상태를 관리한다. */
data class GuardianTutorialState(
    val isRunning: Boolean = false,
    val currentScreen: GuardianTutorialScreen = GuardianTutorialScreen.HOME,
    val homeStep: GuardianHomeTutorialStep = GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
    val analysisStep: GuardianAnalysisTutorialStep = GuardianAnalysisTutorialStep.USER_SELECT,
    val locationStep: GuardianLocationTutorialStep = GuardianLocationTutorialStep.USER_SELECT,
    val settingsStep: GuardianSettingsTutorialStep = GuardianSettingsTutorialStep.ACCESSIBILITY,
    val highlightedBottomTab: GuardianBottomTab? = null,
    val replayTarget: GuardianTutorialReplayTarget? = null,
    val currentNumber: Int = 1,
    val totalNumber: Int = FULL_TUTORIAL_TOTAL
) {
    /** 설정에서 다시 보기를 시작한 상태인지 반환한다. */
    val isReplayMode: Boolean
        get() = replayTarget != null

    /** 최초 전체 안내 또는 전체 다시 보기인지 반환한다. */
    val isFullTutorial: Boolean
        get() = replayTarget == null || replayTarget == GuardianTutorialReplayTarget.FULL

    companion object {
        // 전체 튜토리얼 및 화면별 다시 보기 단계 수.
        const val FULL_TUTORIAL_TOTAL = 23
        const val HOME_REPLAY_TOTAL = 5
        const val ANALYSIS_REPLAY_TOTAL = 3
        const val LOCATION_REPLAY_TOTAL = 6
        const val SETTINGS_REPLAY_TOTAL = 4
    }
}
