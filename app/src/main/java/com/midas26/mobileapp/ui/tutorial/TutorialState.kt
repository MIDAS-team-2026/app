package com.midas26.mobileapp.ui.tutorial

data class TutorialState(
    val isRunning: Boolean = false,
    val currentScreen: TutorialScreen = TutorialScreen.HOME,
    val homeStep: HomeTutorialStep = HomeTutorialStep.MOVE_TO_HOME_TAB,
    val voiceChatStep: VoiceChatTutorialStep = VoiceChatTutorialStep.MESSAGE,
    val analysisStep: AnalysisTutorialStep = AnalysisTutorialStep.MAIN_SCORE,
    val settingsStep: SettingsTutorialStep = SettingsTutorialStep.ACCESSIBILITY,
    val highlightedBottomTab: TutorialBottomTab? = null,
    val currentNumber: Int = 1,
    val totalNumber: Int = 20
)