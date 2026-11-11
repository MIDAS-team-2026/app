package com.midas26.mobileapp.ui.tutorial.guardian

data class GuardianTutorialState(
    val isRunning: Boolean = false,
    val currentScreen: GuardianTutorialScreen =
        GuardianTutorialScreen.HOME,
    val homeStep: GuardianHomeTutorialStep =
        GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
    val analysisStep: GuardianAnalysisTutorialStep =
        GuardianAnalysisTutorialStep.USER_SELECT,
    val locationStep: GuardianLocationTutorialStep =
        GuardianLocationTutorialStep.USER_SELECT,
    val settingsStep: GuardianSettingsTutorialStep =
        GuardianSettingsTutorialStep.ACCESSIBILITY,
    val highlightedBottomTab: GuardianBottomTab? = null,
    val replayTarget: GuardianTutorialReplayTarget? = null,
    val currentNumber: Int = 1,
    val totalNumber: Int = FULL_TUTORIAL_TOTAL
) {
    val isReplayMode: Boolean
        get() = replayTarget != null

    val isFullTutorial: Boolean
        get() = replayTarget == null ||
                replayTarget == GuardianTutorialReplayTarget.FULL

    companion object {
        const val FULL_TUTORIAL_TOTAL = 23
        const val HOME_REPLAY_TOTAL = 5
        const val ANALYSIS_REPLAY_TOTAL = 3
        const val LOCATION_REPLAY_TOTAL = 6
        const val SETTINGS_REPLAY_TOTAL = 4
    }
}