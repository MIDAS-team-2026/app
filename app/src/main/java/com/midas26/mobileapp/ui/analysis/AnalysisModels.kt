package com.midas26.mobileapp.ui.analysis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel

/** 분석 항목 한 줄 (B2 의 4 항목 카드 / B3 의 항목별 추이 공용). */
data class AnalysisItem(
    val emoji: String,
    val label: String,
    val valueText: String,    // 예: "정상", "82%", "1.4s"
    val trendText: String,    // 예: "▲ 안정적", "▲ +5%", "─ 유지"
    val trend: Trend
) {
    enum class Trend { Up, Down, Steady }
}

/** 한 일자의 인지 점수. */
data class DailyScore(val dayLabel: String, val score: Int)

/** 그래프 탭. */
enum class TrendRange { DAY, WEEK, MONTH }

/** 보호자 공유 옵션. */
data class ShareOption(
    val key: String,
    val label: String,
    val description: String,
    val defaultEnabled: Boolean
)

/** 보호자 정보 (공유 모달용 더미). */
data class GuardianInfo(
    val name: String,
    val relationLabel: String,   // "아들 · 연결됨"
    val emoji: String = "👨"
)

/**
 * 분석 ViewModel — 더미.
 * - B1 로딩 단계 (음성 인식/어휘 분석/인지 점수) 진행 시뮬레이션
 * - B2 점수 + 4 항목
 * - B3 7일/30일 추이
 * - B4 보호자 + 공유 옵션 토글
 */
class AnalysisViewModel : ViewModel() {

    /** B1 진행 상태. */
    var loadingPhase by mutableStateOf(LoadingPhase.SCORE)
        private set

    enum class LoadingPhase { VOICE, VOCAB, SCORE, DONE }

    /** B2 헤더 점수. */
    val todayDateLabel = "2026년 5월 5일 · 오늘"
    val todayScore: Int = 75
    val scoreDeltaText: String = "▲ +3"
    val scoreDeltaSubtext: String = "지난 분석 대비 향상되었어요"

    /** B2 4 항목. */
    val todayItems: List<AnalysisItem> = listOf(
        AnalysisItem("🗣️", "발화 속도",  "정상",   "▲ 안정적", AnalysisItem.Trend.Steady),
        AnalysisItem("📚", "어휘 다양성", "82%",   "▲ +5%",   AnalysisItem.Trend.Up),
        AnalysisItem("🧠", "기억 일치도", "78%",   "─ 유지",   AnalysisItem.Trend.Steady),
        AnalysisItem("⚡", "반응 속도",  "1.4s",   "▲ 빨라짐", AnalysisItem.Trend.Up)
    )

    /** B2 코멘트. */
    val comment: String = "꾸준한 점검으로 인지 점수가\n향상되고 있어요. 내일도 함께해요!"

    /** B3 그래프 — 탭별 데이터. */
    var graphRange by mutableStateOf(TrendRange.WEEK)
        private set

    private val weekScores: List<DailyScore> = listOf(
        DailyScore("월", 70),
        DailyScore("화", 68),
        DailyScore("수", 73),
        DailyScore("목", 71),
        DailyScore("금", 74),
        DailyScore("토", 73),
        DailyScore("일", 75)
    )
    private val daySamples: List<DailyScore> = listOf(
        DailyScore("0h", 72), DailyScore("4h", 73), DailyScore("8h", 74),
        DailyScore("12h", 75), DailyScore("16h", 75), DailyScore("20h", 76)
    )
    private val monthSamples: List<DailyScore> = (1..30).map { i ->
        DailyScore("$i", (60 + (i * 17 + 13) % 25))
    }

    val graphPoints: List<DailyScore>
        get() = when (graphRange) {
            TrendRange.DAY -> daySamples
            TrendRange.WEEK -> weekScores
            TrendRange.MONTH -> monthSamples
        }

    val graphAverage: Float
        get() = graphPoints.map { it.score }.average().toFloat()

    val graphAverageDeltaText: String = "평균 +2.3"

    fun selectRange(range: TrendRange) {
        graphRange = range
    }

    /** B3 항목별 추이 (B2 와 일부 항목 다를 수 있음). */
    val trendItems: List<AnalysisItem> = listOf(
        AnalysisItem("🗣️", "발화 속도",  "정상", "▲ +8%",  AnalysisItem.Trend.Up),
        AnalysisItem("📚", "어휘 다양성", "82%", "▲ +5%",  AnalysisItem.Trend.Up),
        AnalysisItem("🧠", "기억 일치도", "78%", "─ 유지",  AnalysisItem.Trend.Steady)
    )

    /** B4 보호자 정보. */
    val guardian: GuardianInfo = GuardianInfo(name = "홍철수 보호자", relationLabel = "아들 · 연결됨")

    /** B4 공유 옵션. */
    val shareOptions: List<ShareOption> = listOf(
        ShareOption("today_score",    "오늘 점수 (75점)",  "점수 + 정상 범위 여부",   defaultEnabled = true),
        ShareOption("detail_items",   "상세 분석 항목",     "발화·어휘·기억·반응",     defaultEnabled = true),
        ShareOption("voice_recording", "대화 녹음 원본",   "보호자가 직접 들어볼 수 있어요", defaultEnabled = false)
    )

    /** B4 토글 상태. */
    val shareSelected = shareOptions.map { it.defaultEnabled }.toMutableStateList()

    fun toggleShareOption(index: Int) {
        if (index in shareSelected.indices) {
            shareSelected[index] = !shareSelected[index]
        }
    }
}
