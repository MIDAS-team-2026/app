package com.midas26.mobileapp.ui.tutorial

/**
 * 앱 전체 튜토리얼의 현재 상태입니다.
 */
data class TutorialState(
    val isRunning: Boolean = false,

    val currentScreen: TutorialScreen =
        TutorialScreen.HOME,

    val homeStep: HomeTutorialStep =
        HomeTutorialStep.WELCOME,

    val voiceChatStep: VoiceChatTutorialStep =
        VoiceChatTutorialStep.MESSAGE,

    val analysisStep: AnalysisTutorialStep =
        AnalysisTutorialStep.MAIN_SCORE,

    val settingsStep: SettingsTutorialStep =
        SettingsTutorialStep.ACCESSIBILITY,

    val currentNumber: Int = 1,

    val totalNumber: Int = 16
)