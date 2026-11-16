package com.midas26.mobileapp.ui.tutorial

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TutorialReplayTarget {
    FULL,
    HOME,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS
}

// 전체 튜토리얼과 화면별 다시 보기의 진행 상태를 관리합니다.
class TutorialViewModel : ViewModel() {

    private val _state = MutableStateFlow(TutorialState())
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    private companion object {
        // 전체 튜토리얼 19단계 (홈 환영 안내 제외)
        const val TOTAL_TUTORIAL_STEPS = 19

        // 화면별 다시 보기에는 하단 탭 이동 안내를 포함하지 않습니다.
        const val HOME_TUTORIAL_STEPS = 4
        const val VOICE_CHAT_TUTORIAL_STEPS = 3
        const val ANALYSIS_TUTORIAL_STEPS = 3
        const val SETTINGS_TUTORIAL_STEPS = 5

        const val FULL_MOVE_TO_HOME_TAB_NUMBER = 1
        const val FULL_HOME_START_NUMBER = 2
        const val FULL_MOVE_TO_VOICE_TAB_NUMBER = 6
        const val FULL_VOICE_CHAT_START_NUMBER = 7
        const val FULL_MOVE_TO_ANALYSIS_TAB_NUMBER = 10
        const val FULL_ANALYSIS_START_NUMBER = 11
        const val FULL_MOVE_TO_SETTINGS_TAB_NUMBER = 14
        const val FULL_SETTINGS_START_NUMBER = 15
    }


    fun isFullTutorial(): Boolean {
        return _state.value.isRunning &&
                _state.value.totalNumber == TOTAL_TUTORIAL_STEPS
    }


    private fun createFullTutorialState(): TutorialState {
        return TutorialState(
            isRunning = true,
            currentScreen = TutorialScreen.HOME,

            homeStep = HomeTutorialStep.MOVE_TO_HOME_TAB,
            voiceChatStep = VoiceChatTutorialStep.MESSAGE,
            analysisStep = AnalysisTutorialStep.MAIN_SCORE,
            settingsStep = SettingsTutorialStep.ACCESSIBILITY,

            highlightedBottomTab = TutorialBottomTab.HOME,

            currentNumber = FULL_MOVE_TO_HOME_TAB_NUMBER,
            totalNumber = TOTAL_TUTORIAL_STEPS
        )
    }


    private fun createHomeTutorialState(): TutorialState {
        return TutorialState(
            isRunning = true,
            currentScreen = TutorialScreen.HOME,

            homeStep = HomeTutorialStep.WEEKLY_CHECK,
            voiceChatStep = VoiceChatTutorialStep.COMPLETED,
            analysisStep = AnalysisTutorialStep.COMPLETED,
            settingsStep = SettingsTutorialStep.COMPLETED,

            highlightedBottomTab = null,

            currentNumber = 1,
            totalNumber = HOME_TUTORIAL_STEPS
        )
    }


    private fun createVoiceChatTutorialState(): TutorialState {
        return TutorialState(
            isRunning = true,
            currentScreen = TutorialScreen.VOICE_CHAT,

            homeStep = HomeTutorialStep.COMPLETED,
            voiceChatStep = VoiceChatTutorialStep.MESSAGE,
            analysisStep = AnalysisTutorialStep.COMPLETED,
            settingsStep = SettingsTutorialStep.COMPLETED,

            highlightedBottomTab = null,

            currentNumber = 1,
            totalNumber = VOICE_CHAT_TUTORIAL_STEPS
        )
    }


    private fun createAnalysisTutorialState(): TutorialState {
        return TutorialState(
            isRunning = true,
            currentScreen = TutorialScreen.ANALYSIS,

            homeStep = HomeTutorialStep.COMPLETED,
            voiceChatStep = VoiceChatTutorialStep.COMPLETED,
            analysisStep = AnalysisTutorialStep.MAIN_SCORE,
            settingsStep = SettingsTutorialStep.COMPLETED,

            highlightedBottomTab = null,

            currentNumber = 1,
            totalNumber = ANALYSIS_TUTORIAL_STEPS
        )
    }


    private fun createSettingsTutorialState(): TutorialState {
        return TutorialState(
            isRunning = true,
            currentScreen = TutorialScreen.SETTINGS,

            homeStep = HomeTutorialStep.COMPLETED,
            voiceChatStep = VoiceChatTutorialStep.COMPLETED,
            analysisStep = AnalysisTutorialStep.COMPLETED,
            settingsStep = SettingsTutorialStep.ACCESSIBILITY,

            highlightedBottomTab = null,

            currentNumber = 1,
            totalNumber = SETTINGS_TUTORIAL_STEPS
        )
    }


    fun startFullTutorial() {
        _state.value = createFullTutorialState()
    }


    fun startHomeTutorial() {
        _state.value = createHomeTutorialState()
    }


    fun startVoiceChatTutorial() {
        _state.value = createVoiceChatTutorialState()
    }

    fun startAnalysisTutorial() {
        _state.value = createAnalysisTutorialState()
    }

    fun startSettingsTutorial() {
        _state.value = createSettingsTutorialState()
    }

    fun startReplayTutorial(target: TutorialReplayTarget) {
        _state.value = when (target) {
            TutorialReplayTarget.FULL -> createFullTutorialState()
            TutorialReplayTarget.HOME -> createHomeTutorialState()
            TutorialReplayTarget.VOICE_CHAT -> createVoiceChatTutorialState()
            TutorialReplayTarget.ANALYSIS -> createAnalysisTutorialState()
            TutorialReplayTarget.SETTINGS -> createSettingsTutorialState()
        }
    }

    // 기존 호출부와 호환되는 전체 튜토리얼 시작 함수입니다.
    fun startTutorial() {
        startFullTutorial()
    }

    // 기존 코드와의 호환성을 위해 유지합니다.
    fun restartTutorial() {
        startFullTutorial()
    }

    fun stopTutorial() {
        _state.update { currentState ->
            currentState.copy(
                isRunning = false,
                highlightedBottomTab = null
            )
        }
    }

    fun showHomeTabGuide(
        number: Int = FULL_MOVE_TO_HOME_TAB_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.HOME,
                homeStep = HomeTutorialStep.MOVE_TO_HOME_TAB,
                highlightedBottomTab = TutorialBottomTab.HOME,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun moveToHome(
        step: HomeTutorialStep = HomeTutorialStep.WEEKLY_CHECK,
        number: Int = FULL_HOME_START_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.HOME,
                homeStep = step,
                highlightedBottomTab = null,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun showVoiceTabGuide(
        number: Int = FULL_MOVE_TO_VOICE_TAB_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.HOME,
                homeStep = HomeTutorialStep.MOVE_TO_VOICE_TAB,
                highlightedBottomTab = TutorialBottomTab.VOICE_CHAT,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun moveToVoiceChat(
        step: VoiceChatTutorialStep = VoiceChatTutorialStep.MESSAGE,
        number: Int = FULL_VOICE_CHAT_START_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.VOICE_CHAT,
                homeStep = HomeTutorialStep.COMPLETED,
                voiceChatStep = step,
                highlightedBottomTab = null,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun showAnalysisTabGuide(
        number: Int = FULL_MOVE_TO_ANALYSIS_TAB_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.VOICE_CHAT,
                voiceChatStep = VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB,
                highlightedBottomTab = TutorialBottomTab.ANALYSIS,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun moveToAnalysis(
        step: AnalysisTutorialStep = AnalysisTutorialStep.MAIN_SCORE,
        number: Int = FULL_ANALYSIS_START_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.ANALYSIS,
                homeStep = HomeTutorialStep.COMPLETED,
                voiceChatStep = VoiceChatTutorialStep.COMPLETED,
                analysisStep = step,
                highlightedBottomTab = null,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun showSettingsTabGuide(
        number: Int = FULL_MOVE_TO_SETTINGS_TAB_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.ANALYSIS,
                analysisStep = AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB,
                highlightedBottomTab = TutorialBottomTab.SETTINGS,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun moveToSettings(
        step: SettingsTutorialStep = SettingsTutorialStep.ACCESSIBILITY,
        number: Int = FULL_SETTINGS_START_NUMBER
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.SETTINGS,
                homeStep = HomeTutorialStep.COMPLETED,
                voiceChatStep = VoiceChatTutorialStep.COMPLETED,
                analysisStep = AnalysisTutorialStep.COMPLETED,
                settingsStep = step,
                highlightedBottomTab = null,
                currentNumber = number.coerceIn(1, TOTAL_TUTORIAL_STEPS),
                totalNumber = TOTAL_TUTORIAL_STEPS
            )
        }
    }

    fun moveHomeStep(
        step: HomeTutorialStep,
        number: Int
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.HOME,
                homeStep = step,
                highlightedBottomTab = when (step) {
                    HomeTutorialStep.MOVE_TO_HOME_TAB ->
                        TutorialBottomTab.HOME

                    HomeTutorialStep.MOVE_TO_VOICE_TAB ->
                        TutorialBottomTab.VOICE_CHAT

                    else -> null
                },
                currentNumber = normalizeNumber(number, currentState.totalNumber)
            )
        }
    }

    fun moveVoiceChatStep(
        step: VoiceChatTutorialStep,
        number: Int
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.VOICE_CHAT,
                voiceChatStep = step,
                highlightedBottomTab = when (step) {
                    VoiceChatTutorialStep.MOVE_TO_ANALYSIS_TAB ->
                        TutorialBottomTab.ANALYSIS
                    else -> null
                },
                currentNumber = normalizeNumber(number, currentState.totalNumber)
            )
        }
    }

    fun moveAnalysisStep(
        step: AnalysisTutorialStep,
        number: Int
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.ANALYSIS,
                analysisStep = step,
                highlightedBottomTab = when (step) {
                    AnalysisTutorialStep.MOVE_TO_SETTINGS_TAB ->
                        TutorialBottomTab.SETTINGS
                    else -> null
                },
                currentNumber = normalizeNumber(number, currentState.totalNumber)
            )
        }
    }

    fun moveSettingsStep(
        step: SettingsTutorialStep,
        number: Int
    ) {
        _state.update { currentState ->
            currentState.copy(
                isRunning = true,
                currentScreen = TutorialScreen.SETTINGS,
                settingsStep = step,
                highlightedBottomTab = null,
                currentNumber = normalizeNumber(number, currentState.totalNumber)
            )
        }
    }

    fun completeTutorial() {
        _state.update { currentState ->
            currentState.copy(
                isRunning = false,
                currentScreen = TutorialScreen.COMPLETED,

                homeStep = HomeTutorialStep.COMPLETED,
                voiceChatStep = VoiceChatTutorialStep.COMPLETED,
                analysisStep = AnalysisTutorialStep.COMPLETED,
                settingsStep = SettingsTutorialStep.COMPLETED,

                highlightedBottomTab = null,

                currentNumber = currentState.totalNumber.coerceAtLeast(1)
            )
        }
    }

    private fun normalizeNumber(
        number: Int,
        totalNumber: Int
    ): Int {
        val safeTotal = totalNumber.coerceAtLeast(1)
        return number.coerceIn(1, safeTotal)
    }
}
