package com.midas26.mobileapp.ui.recall

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 회상 과제 한 문항. */
data class RecallQuestion(
    val number: Int,
    val total: Int,
    val categoryIcon: ImageVector,
    val category: String,
    val text: String,
    val hint: String?
)

/** 회상 결과 (점수 / 상세 항목). */
data class RecallScore(
    val total: Int,
    val isNormal: Boolean,
    val items: List<DetailItem>,
    val note: String?
) {
    data class DetailItem(
        val label: String,
        val percent: Int,    // 0..100
        val isWarning: Boolean = false
    )
}

/** 한 문항 안에서의 상태. */
sealed interface RecallState {
    /** 답변 대기 (큰 마이크 버튼). */
    data object Idle : RecallState
    /** 녹음 중 (정지 버튼 + 음파 + 타이머). */
    data class Recording(val seconds: Int) : RecallState
}

/**
 * 회상 과제 ViewModel — 더미 구현.
 *
 *  - 5문항 미리 정의
 *  - 녹음 시작/정지 시뮬레이션 (타이머)
 *  - 정지하면 0.5초 후 자동으로 다음 문항 또는 결과 페이지 이동 신호 발사
 */
class RecallViewModel : ViewModel() {

    val questions: List<RecallQuestion> = listOf(
        RecallQuestion(
            number = 1, total = 5,
            categoryIcon = Icons.Default.CalendarToday, category = "어제 대화 회상",
            text = "어제 어떤 일이 있었는지\n기억나시는 대로 말씀해주세요",
            hint = "어제 누구와 어디서 무엇을\n하셨는지 떠올려보세요"
        ),
        RecallQuestion(
            number = 2, total = 5,
            categoryIcon = Icons.Default.CalendarToday, category = "어제 대화 회상",
            text = "어제 어떤 음식을\n드셨다고 말씀하셨죠?",
            hint = "어제 점심 시간에 대화하셨어요.\n가족 분과 함께 드신 메뉴였습니다."
        ),
        RecallQuestion(
            number = 3, total = 5,
            categoryIcon = Icons.Default.Abc, category = "단어/장소 기억",
            text = "오늘 아침에 들으신\n세 단어를 기억하시나요?",
            hint = "사과, 자동차, 우산 — 비슷한 발음의\n단어가 떠오를 수 있어요"
        ),
        RecallQuestion(
            number = 4, total = 5,
            categoryIcon = Icons.Default.LocationOn, category = "단어/장소 기억",
            text = "어제 다녀오신\n장소가 어디였나요?",
            hint = "어제 오후에 외출하셨어요.\n병원, 공원, 시장 중 한 곳이었습니다."
        ),
        RecallQuestion(
            number = 5, total = 5,
            categoryIcon = Icons.Default.Timer, category = "시간/날짜 인지",
            text = "오늘은 몇 월 며칠\n무슨 요일인가요?",
            hint = "달력을 보지 않고\n천천히 떠올려보세요"
        )
    )

    /** 현재 문항 인덱스 (0..questions.size-1). */
    var currentIndex by mutableStateOf(0)
        private set

    /** 현재 문항의 상태. */
    var state: RecallState by mutableStateOf(RecallState.Idle)
        private set

    /** 모든 문항이 끝나면 결과 페이지로 이동하라는 신호. */
    var requestNavigateToResult by mutableStateOf(false)
        private set

    private var timerJob: Job? = null

    val currentQuestion: RecallQuestion get() = questions[currentIndex]

    fun startRecording() {
        if (state !is RecallState.Idle) return
        state = RecallState.Recording(0)
        timerJob = viewModelScope.launch {
            var s = 0
            while (true) {
                delay(1000)
                s += 1
                state = RecallState.Recording(s)
            }
        }
    }

    fun stopRecording() {
        if (state !is RecallState.Recording) return
        timerJob?.cancel()
        timerJob = null
        // 0.5초 후 다음 문항 또는 결과 이동
        viewModelScope.launch {
            delay(500)
            if (currentIndex < questions.lastIndex) {
                currentIndex += 1
                state = RecallState.Idle
            } else {
                // 마지막 문항 → 결과 페이지
                requestNavigateToResult = true
            }
        }
    }

    fun consumeResultNavigation() {
        requestNavigateToResult = false
    }

    /** 더미 결과 — 실제로는 답변 분석 결과를 백엔드에서 받아야 함. */
    fun computeResult(): RecallScore = RecallScore(
        total = 82,
        isNormal = true,
        items = listOf(
            RecallScore.DetailItem("단어 일치도", 90),
            RecallScore.DetailItem("상황 일치도", 78),
            RecallScore.DetailItem("시간 인지", 75, isWarning = true)
        ),
        note = "어제 점심의 시간 인지가\n약간 낮아요. 매일 점검을\n이어가세요."
    )

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
