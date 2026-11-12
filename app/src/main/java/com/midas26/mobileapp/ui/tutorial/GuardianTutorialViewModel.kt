package com.midas26.mobileapp.ui.tutorial.guardian

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GuardianTutorialViewModel : ViewModel() {

    private val _state = MutableStateFlow(GuardianTutorialState())
    val state: StateFlow<GuardianTutorialState> = _state.asStateFlow()

    /** 최초 실행 시 전체 보호자 튜토리얼을 1단계부터 시작한다. */
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

    /** 선택한 다시 보기 범위에 맞춰 시작 화면과 단계 수를 설정한다. */
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

    /** 사용자 선택 후 분석 결과의 첫 안내 단계로 이동한다. */
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

    /** 분석 결과 안내를 마치고 하단 위치 탭을 강조한다. */
    fun showLocationTabGuide(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.ANALYSIS_RESULT,
                analysisStep =
                    GuardianAnalysisTutorialStep.MOVE_TO_LOCATION_TAB,
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

    /** 위치 경로 결과 화면의 안내를 시작한다. */
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

    /** 위치 안내를 마치고 하단 설정 탭을 강조한다. */
    fun showSettingsTabGuide(number: Int) {
        _state.update {
            it.copy(
                isRunning = true,
                currentScreen =
                    GuardianTutorialScreen.LOCATION_DETAIL,
                locationStep =
                    GuardianLocationTutorialStep.MOVE_TO_SETTINGS_TAB,
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

    /** 전체 튜토리얼 진행 여부를 반환한다. */
    fun isFullTutorial(): Boolean =
        _state.value.isFullTutorial

    /** 현재 다시 보기 대상(최초 실행 시 null)을 반환한다. */
    fun currentReplayTarget(): GuardianTutorialReplayTarget? =
        _state.value.replayTarget

    /** 건너뛰기 또는 종료 시 진행 상태를 초기화한다. */
    fun stopTutorial() {
        _state.value = GuardianTutorialState()
    }

    /** 완료 상태를 기록하고 하단 탭 강조를 해제한다. */
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
