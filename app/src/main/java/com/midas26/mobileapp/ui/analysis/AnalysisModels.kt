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
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    /** 종합 점수 (0–100 스케일 가정). */
    val todayScore: Int get() = finalRiskScore?.let { (it * 100).roundToInt() } ?: 0

    /** 헤더 배지 — 위험 등급. */
    val scoreDeltaText: String get() = riskLevel ?: "─"

    /** 어제 대비 점수 변화 문구. 히스토리 API 연동 전까지 weekScores 기반. */
    val yesterdayCompareText: String
        get() {
            val today = weekScores.lastOrNull()?.score ?: return ""
            val yesterday = weekScores.getOrNull(weekScores.size - 2)?.score ?: return ""
            val delta = today - yesterday
            return when {
                delta > 0 -> "어제보다 ${delta}점 올랐어요"
                delta < 0 -> "어제보다 ${-delta}점 내렸어요"
                else      -> "어제와 같은 점수예요"
            }
        }

    // ── 4 카드 ─────────────────────────────────────────────────────────────────

    val todayItems: List<AnalysisItem>
        get() = listOf(
            AnalysisItem(
                icon      = Icons.Default.BarChart,
                label     = "종합 위험도",
                valueText = riskLevel ?: "-",
                trendText = "",
                trend     = AnalysisItem.Trend.Steady
            ),
            AnalysisItem(
                icon      = Icons.Default.RecordVoiceOver,
                label     = "음성 점수",
                valueText = speechScore?.let { "${(it * 100).roundToInt()}점" } ?: "-",
                trendText = "",
                trend     = AnalysisItem.Trend.Steady
            ),
            AnalysisItem(
                icon      = Icons.Default.Psychology,
                label     = "회상 점수",
                valueText = recallScore?.let { "${it.roundToInt()}점" } ?: "-",
                trendText = "",
                trend     = AnalysisItem.Trend.Steady
            ),
            AnalysisItem(
                icon      = Icons.AutoMirrored.Filled.MenuBook,
                label     = "텍스트 점수",
                valueText = textScore?.let { "${(it * 100).roundToInt()}점" } ?: "-",
                trendText = "",
                trend     = AnalysisItem.Trend.Steady
            )
        )

    // ── 주간 그래프 (더미 유지 — 히스토리 API 추가 전까지) ────────────────────

    var graphRange by mutableStateOf(TrendRange.WEEK)
        private set

    private val weekScores: List<DailyScore> = listOf(
        DailyScore("월", 70), DailyScore("화", 68), DailyScore("수", 73),
        DailyScore("목", 71), DailyScore("금", 74), DailyScore("토", 73),
        DailyScore("일", 75)
    )

    val graphPoints: List<DailyScore> get() = weekScores

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
