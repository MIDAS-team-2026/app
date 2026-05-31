package com.midas26.mobileapp.ui.analysis

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.util.PrefsManager
import com.midas26.mobileapp.network.DailyScoreResponse
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 분석 항목 한 줄 (4개 카드 공용). */
data class AnalysisItem(
    val icon: ImageVector,
    val label: String,
    val valueText: String,
    val trendText: String,
    val trend: Trend
) {
    enum class Trend { Up, Down, Steady }
}

/** 한 일자의 인지 점수. */
data class DailyScore(val dayLabel: String, val score: Int, val date: String? = null)

/** 그래프 탭. */
enum class TrendRange { DAY, WEEK, MONTH }

/** 보호자 공유 옵션. */
data class ShareOption(
    val key: String,
    val label: String,
    val description: String,
    val defaultEnabled: Boolean
)

/** 보호자 정보 (공유 모달용). */
data class GuardianInfo(
    val name: String,
    val relationLabel: String,
    val icon: ImageVector = Icons.Default.Person
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager.from(application)
    private val api   = RetrofitClient.analysis

    // ── 로딩 상태 ─────────────────────────────────────────────────────────────

    var isLoading by mutableStateOf(false)
        private set

    // ── API 데이터 ─────────────────────────────────────────────────────────────

    private var finalRiskScore by mutableStateOf<Float?>(null)
    private var riskLevel      by mutableStateOf<String?>(null)
    private var speechScore    by mutableStateOf<Float?>(null)
    private var textScore      by mutableStateOf<Float?>(null)
    private var recallScore    by mutableStateOf<Float?>(null)
    private var analyzedAt     by mutableStateOf<String?>(null)

    init {
        loadSummary()
        loadWeeklyScores()
        loadStreak()
    }

    fun loadSummary() {
        val userId = prefs.getUserId()
        if (userId <= 0) return
        viewModelScope.launch {
            isLoading = true
            var loaded = false

            // 오늘 데이터 시도
            runCatching { api.getTodaySummary(userId) }
                .onSuccess { resp ->
                    resp.body()?.data?.let { body ->
                        applyBody(body)
                        loaded = true
                    }
                }

            // 없으면 최신 데이터로 폴백
            if (!loaded) {
                runCatching { api.getLatestSummary(userId) }
                    .onSuccess { resp ->
                        resp.body()?.data?.let { body -> applyBody(body) }
                    }
            }

            isLoading = false
        }
    }

    fun refresh() {
        loadSummary()
        loadWeeklyScores()
        loadStreak()
    }

    private fun loadStreak() {
        val userId = prefs.getUserId()
        if (userId <= 0) return
        viewModelScope.launch {
            runCatching { api.getStreakDays(userId) }
                .onSuccess { resp -> resp.body()?.data?.let { streakDays = it } }
        }
    }

    // ── 홈 화면용 ─────────────────────────────────────────────────────────────

    /** 최근 7일 각 날짜에 분석 데이터가 있는지 여부 (index 0 = 6일 전, index 6 = 오늘) */
    val weeklyChecks: List<Boolean>
        get() = if (graphPoints.size == 7) {
            graphPoints.map { it.score > 0 }
        } else {
            List(7) { false }
        }

    /** 최근 7일 요일 라벨 (index 0 = 6일 전, index 6 = 오늘) */
    val weeklyDayLabels: List<String>
        get() {
            val today = LocalDate.now()
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            return (6 downTo 0).map { daysAgo ->
                val date = today.minusDays(daysAgo.toLong())
                dayNames[date.dayOfWeek.value % 7]
            }
        }

    /** 오늘은 항상 마지막(index 6) */
    val todayDayIndex: Int get() = 6

    private fun applyBody(body: com.midas26.mobileapp.network.SessionSummaryResponse) {
        finalRiskScore = body.finalRiskScore
        riskLevel      = body.riskLevel
        speechScore    = body.speechScore
        textScore      = body.textScore
        recallScore    = body.recallScore
        analyzedAt     = body.analyzedAt
    }

    // ── 헤더 ──────────────────────────────────────────────────────────────────

    val todayDateLabel: String
        get() {
            val raw = analyzedAt ?: return "분석 결과"
            return try {
                val date = LocalDate.parse(raw.substring(0, 10))
                val today = LocalDate.now()
                val prefix = if (date.year != today.year) "${date.year}년 " else ""
                "${prefix}${date.monthValue}월 ${date.dayOfMonth}일 분석 결과"
            } catch (e: Exception) {
                "분석 결과"
            }
        }

    /** 오늘 데이터를 보고 있는지 여부 (선택 날짜가 없거나 오늘이면 true) */
    val isViewingToday: Boolean get() {
        val selDate = selectedDayScore?.date ?: return true
        return try {
            LocalDate.parse(selDate.substring(0, 10)) == LocalDate.now()
        } catch (e: Exception) { true }
    }

    /** 헤더에 표시할 종합 점수 */
    val displayScore: Int get() = selectedDayScore
        ?.finalRiskScore?.let { (it * 100).roundToInt() }
        ?: finalRiskScore?.let { (it * 100).roundToInt() }
        ?: 0

    /** 헤더 배지 — 위험 등급 */
    val displayRiskLevel: String get() = selectedDayScore?.riskLevel ?: riskLevel ?: "─"

    /** 헤더 날짜 라벨 */
    val displayDateLabel: String
        get() {
            if (isViewingToday) return "오늘의 분석 결과"
            val raw = selectedDayScore?.date ?: analyzedAt ?: return "분석 결과"
            return try {
                val date = LocalDate.parse(raw.substring(0, 10))
                val today = LocalDate.now()
                val prefix = if (date.year != today.year) "${date.year}년 " else ""
                "${prefix}${date.monthValue}월 ${date.dayOfMonth}일 분석 결과"
            } catch (e: Exception) { "분석 결과" }
        }

    /** 어제 대비 점수 변화 문구 — 주간 그래프 데이터 기반. */
    val yesterdayCompareText: String
        get() {
            if (!isViewingToday) return ""
            val points = graphPoints.filter { it.score > 0 }
            val today = points.lastOrNull()?.score ?: return ""
            val yesterday = points.getOrNull(points.size - 2)?.score ?: return ""
            val delta = today - yesterday
            return when {
                delta > 0 -> "어제보다 ${delta}점 올랐어요"
                delta < 0 -> "어제보다 ${-delta}점 내렸어요"
                else      -> "어제와 같은 점수예요"
            }
        }

    // ── 4 카드 ─────────────────────────────────────────────────────────────────

    val todayItems: List<AnalysisItem>
        get() {
            val sel = selectedDayScore
            val dRisk    = sel?.riskLevel    ?: riskLevel
            val dSpeech  = sel?.speechScore  ?: speechScore
            val dRecall  = sel?.recallScore  ?: recallScore
            val dText    = sel?.textScore    ?: textScore
            return listOf(
                AnalysisItem(
                    icon      = Icons.Default.BarChart,
                    label     = "종합 위험도",
                    valueText = dRisk ?: "-",
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                ),
                AnalysisItem(
                    icon      = Icons.Default.RecordVoiceOver,
                    label     = "음성 점수",
                    valueText = dSpeech?.let { "${(it * 100).roundToInt()}점" } ?: "-",
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                ),
                AnalysisItem(
                    icon      = Icons.Default.Psychology,
                    label     = "회상 점수",
                    valueText = dRecall?.let { "${it.roundToInt()}점" } ?: "-",
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                ),
                AnalysisItem(
                    icon      = Icons.AutoMirrored.Filled.MenuBook,
                    label     = "텍스트 점수",
                    valueText = dText?.let { "${(it * 100).roundToInt()}점" } ?: "-",
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                )
            )
        }

    // ── 주간 그래프 ───────────────────────────────────────────────────────────

    var graphRange by mutableStateOf(TrendRange.WEEK)
        private set

    var graphPoints by mutableStateOf<List<DailyScore>>(emptyList())
        private set

    var streakDays by mutableStateOf(0)
        private set

    // 포인트 탭 시 선택된 날짜 상세
    var selectedDayScore by mutableStateOf<DailyScoreResponse?>(null)
        private set
    var isDayLoading by mutableStateOf(false)
        private set

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun loadWeeklyScores() {
        val userId = prefs.getUserId()
        if (userId <= 0) return
        viewModelScope.launch {
            runCatching { api.getWeeklyScores(userId) }
                .onSuccess { resp ->
                    resp.body()?.data?.let { list ->
                        graphPoints = list.map { d ->
                            val date = d.date?.let { LocalDate.parse(it) }
                            val label = when (date?.dayOfWeek?.value) {
                                1 -> "월"; 2 -> "화"; 3 -> "수"; 4 -> "목"
                                5 -> "금"; 6 -> "토"; 7 -> "일"; else -> ""
                            }
                            DailyScore(
                                dayLabel = label,
                                score    = d.finalRiskScore?.let { (it * 100).roundToInt() } ?: 0,
                                date     = d.date
                            )
                        }
                    }
                }
        }
    }

    fun onGraphPointTapped(dateStr: String?) {
        val userId = prefs.getUserId()
        if (userId <= 0 || dateStr == null) return
        viewModelScope.launch {
            isDayLoading = true
            runCatching { api.getDailyScore(userId, dateStr) }
                .onSuccess { resp -> selectedDayScore = resp.body()?.data }
            isDayLoading = false
        }
    }

    fun clearSelectedDay() { selectedDayScore = null }

    fun selectRange(range: TrendRange) { graphRange = range }

    // ── 보호자 공유 (더미) ────────────────────────────────────────────────────

    val guardian: GuardianInfo = GuardianInfo(name = "홍철수 보호자", relationLabel = "아들 · 연결됨")

    val shareOptions: List<ShareOption> = listOf(
        ShareOption("today_score",     "오늘 점수",     "점수 + 정상 범위 여부",          defaultEnabled = true),
        ShareOption("detail_items",    "상세 분석 항목", "음성·회상·텍스트 점수",          defaultEnabled = true),
        ShareOption("voice_recording", "대화 녹음 원본", "보호자가 직접 들어볼 수 있어요", defaultEnabled = false)
    )

    val shareSelected = shareOptions.map { it.defaultEnabled }.toMutableStateList()

    fun toggleShareOption(index: Int) {
        if (index in shareSelected.indices) shareSelected[index] = !shareSelected[index]
    }
}
