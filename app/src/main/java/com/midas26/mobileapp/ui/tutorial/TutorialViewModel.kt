package com.midas26.mobileapp.ui.tutorial

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 설정의 '앱 사용법 다시 보기'에서 선택할 수 있는 튜토리얼 종류입니다.
 */
enum class TutorialReplayTarget {
    FULL,
    HOME,
    VOICE_CHAT,
    ANALYSIS,
    SETTINGS
}

/**
 * 전체 튜토리얼과 화면별 다시 보기를 관리합니다.
 *
 * 주의:
 * - 최초 전체 튜토리얼 완료 여부는 PrefsManager에서 관리합니다.
 * - 이 ViewModel은 화면/단계/번호와 실행 상태만 관리합니다.
 * - 화면별 다시 보기는 최초 완료 기록을 초기화하지 않습니다.
 */
class TutorialViewModel : ViewModel() {

    private val _state = MutableStateFlow(TutorialState())
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    private companion object {
        /*
         * 기존 화면 설명 16단계에 하단 탭 안내 3단계를 추가합니다.
         *
         * 1     : 홈 탭 안내
         * 2~6   : 홈 화면
         * 7     : 대화 탭 안내
         * 8~10  : 음성 대화
         * 11    : 분석 탭 안내
         * 12~14 : 분석 결과
         * 15    : 설정 탭 안내
         * 16~21 : 설정
         */
        const val TOTAL_TUTORIAL_STEPS = 21

        // 화면별 다시 보기에는 하단 탭 이동 안내를 포함하지 않습니다.
        const val HOME_TUTORIAL_STEPS = 5
        const val VOICE_CHAT_TUTORIAL_STEPS = 3
        const val ANALYSIS_TUTORIAL_STEPS = 3
        const val SETTINGS_TUTORIAL_STEPS = 6

        const val FULL_MOVE_TO_HOME_TAB_NUMBER = 1
        const val FULL_HOME_START_NUMBER = 2
        const val FULL_MOVE_TO_VOICE_TAB_NUMBER = 7
        const val FULL_VOICE_CHAT_START_NUMBER = 8
        const val FULL_MOVE_TO_ANALYSIS_TAB_NUMBER = 11
        const val FULL_ANALYSIS_START_NUMBER = 12
        const val FULL_MOVE_TO_SETTINGS_TAB_NUMBER = 15
        const val FULL_SETTINGS_START_NUMBER = 16
    }

    /**
     * 현재 실행 중인 튜토리얼이 전체 튜토리얼인지 확인합니다.
     *
     * 화면에서 마지막 단계 이후 다음 화면으로 이동할지,
     * 현재 화면 튜토리얼만 종료할지 판단할 때 사용할 수 있습니다.
     */
    fun isFullTutorial(): Boolean {
        return _state.value.isRunning &&
                _state.value.totalNumber == TOTAL_TUTORIAL_STEPS
    }

    /**
     * 전체 튜토리얼의 초기 상태를 생성합니다.
     */
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

    /**
     * 홈 화면 튜토리얼만 실행하는 초기 상태를 생성합니다.
     *
     * 현재 화면 이외의 단계는 COMPLETED로 초기화하여
     * 이전 튜토리얼 상태가 다음 다시 보기에 섞이지 않도록 합니다.
     */
    private fun createHomeTutorialState(): TutorialState {
        return TutorialState(
            isRunning = true,
            currentScreen = TutorialScreen.HOME,

            homeStep = HomeTutorialStep.WELCOME,
            voiceChatStep = VoiceChatTutorialStep.COMPLETED,
            analysisStep = AnalysisTutorialStep.COMPLETED,
            settingsStep = SettingsTutorialStep.COMPLETED,

            highlightedBottomTab = null,

            currentNumber = 1,
            totalNumber = HOME_TUTORIAL_STEPS
        )
    }

    /**
     * 음성 대화 화면 튜토리얼만 실행하는 초기 상태를 생성합니다.
     */
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

    /**
     * 분석 결과 화면 튜토리얼만 실행하는 초기 상태를 생성합니다.
     */
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

    /**
     * 설정 화면 튜토리얼만 실행하는 초기 상태를 생성합니다.
     */
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

    /**
     * 최초 가입 사용자를 위한 전체 튜토리얼을 시작합니다.
     */
    fun startFullTutorial() {
        _state.value = createFullTutorialState()
    }

    /**
     * 홈 화면 사용법만 시작합니다.
     */
    fun startHomeTutorial() {
        _state.value = createHomeTutorialState()
    }

    /**
     * 음성 대화 화면 사용법만 시작합니다.
     */
    fun startVoiceChatTutorial() {
        _state.value = createVoiceChatTutorialState()
    }

    /**
     * 분석 결과 화면 사용법만 시작합니다.
     */
    fun startAnalysisTutorial() {
        _state.value = createAnalysisTutorialState()
    }

    /**
     * 설정 화면 사용법만 시작합니다.
     */
    fun startSettingsTutorial() {
        _state.value = createSettingsTutorialState()
    }

    /**
     * 설정 화면에서 선택한 사용법을 완전히 새로운 상태로 시작합니다.
     *
     * 이전 화면의 currentScreen, 단계, 번호가 남지 않도록
     * 기존 상태를 copy하지 않고 새 TutorialState를 대입합니다.
     */
    fun startReplayTutorial(target: TutorialReplayTarget) {
        _state.value = when (target) {
            TutorialReplayTarget.FULL -> createFullTutorialState()
            TutorialReplayTarget.HOME -> createHomeTutorialState()
            TutorialReplayTarget.VOICE_CHAT -> createVoiceChatTutorialState()
            TutorialReplayTarget.ANALYSIS -> createAnalysisTutorialState()
            TutorialReplayTarget.SETTINGS -> createSettingsTutorialState()
        }
    }

    /**
     * 기존 코드와의 호환성을 위해 유지합니다.
     * 최초 자동 실행은 전체 튜토리얼로 시작합니다.
     */
    fun startTutorial() {
        startFullTutorial()
    }

    /**
     * 기존 코드와의 호환성을 위해 유지합니다.
     * 전체 사용법을 처음부터 다시 시작합니다.
     */
    fun restartTutorial() {
        startFullTutorial()
    }

    /**
     * 튜토리얼을 중단합니다.
     *
     * 다음 다시 보기를 시작할 때는 startReplayTutorial()이
     * 새 상태를 생성하므로 현재 단계가 남아 있어도 영향을 주지 않습니다.
     */
    fun stopTutorial() {
        _state.update { currentState ->
            currentState.copy(
                isRunning = false,
                highlightedBottomTab = null
            )
        }
    }

    /**
     * 전체 튜토리얼 시작 시 하단 홈 탭을 안내합니다.
     */
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

    /**
     * 전체 튜토리얼 진행 중 홈 화면으로 이동합니다.
     */
    fun moveToHome(
        step: HomeTutorialStep = HomeTutorialStep.WELCOME,
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

    /**
     * 홈 화면 설명이 끝난 뒤 하단의 대화 탭을 안내합니다.
     */
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

    /**
     * 하단 대화 탭을 누른 뒤 음성 대화 튜토리얼을 시작합니다.
     */
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

    /**
     * 음성 대화 설명이 끝난 뒤 하단의 분석 탭을 안내합니다.
     */
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

    /**
     * 하단 분석 탭을 누른 뒤 분석 결과 튜토리얼을 시작합니다.
     */
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

    /**
     * 분석 결과 설명이 끝난 뒤 하단의 설정 탭을 안내합니다.
     */
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

    /**
     * 하단 설정 탭을 누른 뒤 설정 튜토리얼을 시작합니다.
     */
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

    /**
     * 홈 화면의 특정 단계로 이동합니다.
     *
     * 현재 totalNumber를 유지하므로 전체 튜토리얼과
     * 홈 화면 다시 보기 모두에서 사용할 수 있습니다.
     */
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

    /**
     * 음성 대화 화면의 특정 단계로 이동합니다.
     */
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

    /**
     * 분석 결과 화면의 특정 단계로 이동합니다.
     */
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

    /**
     * 설정 화면의 특정 단계로 이동합니다.
     */
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

    /**
     * 현재 실행 중인 튜토리얼을 완료합니다.
     *
     * 전체/부분 튜토리얼 모두 실행 상태만 종료합니다.
     * 최초 전체 튜토리얼의 영구 완료 저장은 PrefsManager에서 처리해야 합니다.
     */
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

    /**
     * 잘못된 단계 번호가 들어오더라도 1..totalNumber 범위로 보정합니다.
     */
    private fun normalizeNumber(
        number: Int,
        totalNumber: Int
    ): Int {
        val safeTotal = totalNumber.coerceAtLeast(1)
        return number.coerceIn(1, safeTotal)
    }
}