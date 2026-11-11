package com.midas26.mobileapp.ui.tutorial.guardian

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GuardianTutorialViewModel : ViewModel() {

    private val _state =
        MutableStateFlow(GuardianTutorialState())

    val state: StateFlow<GuardianTutorialState> =
        _state.asStateFlow()

    /**
     * 최초 실행 또는 전체 사용법 다시 보기에서 호출합니다.
     */
    fun startFullTutorial() {
        _state.value = GuardianTutorialState(
            isRunning = true,
            currentScreen = GuardianTutorialScreen.HOME,
            homeStep = GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
            highlightedBottomTab = GuardianBottomTab.HOME,
            replayTarget = null,
            currentNumber = 1,
            totalNumber =
                GuardianTutorialState.FULL_TUTORIAL_TOTAL
        )
    }

    /**
     * 설정 화면에서 선택한 파트만 다시 시작합니다.
     */
    fun startReplayTutorial(
        target: GuardianTutorialReplayTarget
    ) {
        _state.value = when (target) {
            GuardianTutorialReplayTarget.FULL ->
                GuardianTutorialState(
                    isRunning = true,
                    currentScreen = GuardianTutorialScreen.HOME,
                    homeStep =
                        GuardianHomeTutorialStep.MOVE_TO_HOME_TAB,
                    highlightedBottomTab = GuardianBottomTab.HOME,
                    replayTarget =
                        GuardianTutorialReplayTarget.FULL,
                    currentNumber = 1,
                    totalNumber =
                        GuardianTutorialState.FULL_TUTORIAL_TOTAL
                )

            GuardianTutorialReplayTarget.HOME ->
                GuardianTutorialState(
                    isRunning = true,
                    currentScreen = GuardianTutorialScreen.HOME,
                    homeStep =
                        GuardianHomeTutorialStep.LINKED_USER,
                    highlightedBottomTab = null,
                    replayTarget =
                        GuardianTutorialReplayTarget.HOME,
                    currentNumber = 1,
                    totalNumber =
                        GuardianTutorialState.HOME_REPLAY_TOTAL
                )

            GuardianTutorialReplayTarget.ANALYSIS ->
                GuardianTutorialState(
                    isRunning = true,
                    currentScreen =
                        GuardianTutorialScreen.ANALYSIS_RESULT,
                    analysisStep =
                        GuardianAnalysisTutorialStep.MAIN_SCORE,
                    highlightedBottomTab = null,
                    replayTarget =
                        GuardianTutorialReplayTarget.ANALYSIS,
                    currentNumber = 1,
                    totalNumber =
                        GuardianTutorialState.ANALYSIS_REPLAY_TOTAL
                )

            GuardianTutorialReplayTarget.LOCATION ->
                GuardianTutorialState(
                    isRunning = true,
                    currentScreen =
                        GuardianTutorialScreen.LOCATION_SELECT,
                    locationStep =
                        GuardianLocationTutorialStep.USER_SELECT,
                    highlightedBottomTab = null,
                    replayTarget =
                        GuardianTutorialReplayTarget.LOCATION,
                    currentNumber = 1,
                    totalNumber =
                        GuardianTutorialState.LOCATION_REPLAY_TOTAL
                )

            GuardianTutorialReplayTarget.SETTINGS ->
                GuardianTutorialState(
                    isRunning = true,
                    currentScreen =
                        GuardianTutorialScreen.SETTINGS,
                    settingsStep =
                        GuardianSettingsTutorialStep.ACCESSIBILITY,
                    highlightedBottomTab = null,
                    replayTarget =
                        GuardianTutorialReplayTarget.SETTINGS,
                    currentNumber = 1,
                    totalNumber =
                        GuardianTutorialState.SETTINGS_REPLAY_TOTAL
                )
        }
    }

    fun moveToHome(
        step: GuardianHomeTutorialStep =
            GuardianHomeTutorialStep.LINKED_USER,
        number: Int = 2
    ) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen = GuardianTutorialScreen.HOME,
                homeStep = step,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun moveHomeStep(
        step: GuardianHomeTutorialStep,
        number: Int
    ) {
        moveToHome(
            step = step,
            number = number
        )
    }

    fun showAnalysisTabGuide(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen = GuardianTutorialScreen.HOME,
                homeStep =
                    GuardianHomeTutorialStep.MOVE_TO_ANALYSIS_TAB,
                highlightedBottomTab =
                    GuardianBottomTab.ANALYSIS,
                currentNumber = number
            )
        }
    }

    fun moveToAnalysis(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.ANALYSIS_SELECT,
                analysisStep =
                    GuardianAnalysisTutorialStep.USER_SELECT,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    /**
     * 분석할 사용자를 선택한 뒤 호출합니다.
     * 분석 결과의 첫 단계인 오늘의 인지 점수부터 시작합니다.
     */
    fun moveToAnalysisResult(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.ANALYSIS_RESULT,
                analysisStep =
                    GuardianAnalysisTutorialStep.MAIN_SCORE,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun moveAnalysisStep(
        step: GuardianAnalysisTutorialStep,
        number: Int
    ) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.ANALYSIS_RESULT,
                analysisStep = step,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun showLocationTabGuide(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.ANALYSIS_RESULT,
                analysisStep =
                    GuardianAnalysisTutorialStep
                        .MOVE_TO_LOCATION_TAB,
                highlightedBottomTab =
                    GuardianBottomTab.LOCATION,
                currentNumber = number
            )
        }
    }

    fun moveToLocation(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.LOCATION_SELECT,
                locationStep =
                    GuardianLocationTutorialStep.USER_SELECT,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun moveToLocationDetail(
        step: GuardianLocationTutorialStep =
            GuardianLocationTutorialStep.CURRENT_LOCATION,
        number: Int
    ) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.LOCATION_DETAIL,
                locationStep = step,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun moveLocationStep(
        step: GuardianLocationTutorialStep,
        number: Int,
        screen: GuardianTutorialScreen =
            GuardianTutorialScreen.LOCATION_DETAIL
    ) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen = screen,
                locationStep = step,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun moveToLocationRoute(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.LOCATION_ROUTE,
                locationStep =
                    GuardianLocationTutorialStep.ROUTE_RESULT,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun showSettingsTabGuide(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.LOCATION_DETAIL,
                locationStep =
                    GuardianLocationTutorialStep
                        .MOVE_TO_SETTINGS_TAB,
                highlightedBottomTab =
                    GuardianBottomTab.SETTINGS,
                currentNumber = number
            )
        }
    }

    fun moveToSettings(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.SETTINGS,
                settingsStep =
                    GuardianSettingsTutorialStep.ACCESSIBILITY,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    fun moveSettingsStep(
        step: GuardianSettingsTutorialStep,
        number: Int
    ) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.SETTINGS,
                settingsStep = step,
                highlightedBottomTab = null,
                currentNumber = number
            )
        }
    }

    /**
     * 현재 실행이 전체 튜토리얼인지 확인합니다.
     */
    fun isFullTutorial(): Boolean =
        _state.value.isFullTutorial

    fun currentReplayTarget():
            GuardianTutorialReplayTarget? =
        _state.value.replayTarget

    fun stopTutorial() {
        _state.update {
            it.copy(
                isRunning = false,
                highlightedBottomTab = null
            )
        }
    }

    fun completeTutorial() {
        _state.update {
            it.copy(
                isRunning = false,
                currentScreen =
                    GuardianTutorialScreen.COMPLETED,
                homeStep =
                    GuardianHomeTutorialStep.COMPLETED,
                analysisStep =
                    GuardianAnalysisTutorialStep.COMPLETED,
                locationStep =
                    GuardianLocationTutorialStep.COMPLETED,
                settingsStep =
                    GuardianSettingsTutorialStep.COMPLETED,
                highlightedBottomTab = null,
                currentNumber = it.totalNumber
            )
        }
    }
}