package com.midas26.mobileapp.ui.analysis

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.util.PrefsManager
import com.midas26.mobileapp.network.DailyScoreResponse
import com.midas26.mobileapp.network.LinguisticMarkerResponse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** 분석 항목 한 줄 (4개 카드 공용). */
data class AnalysisItem(
    val icon: ImageVector,
    val label: String,
    val valueText: String,
    val trendText: String,
    val trend: Trend
) {
    enum class Trend { Steady }
}

/** 한 일자의 인지 건강 점수. 앱 표시 점수는 높을수록 양호하다. */
data class DailyScore(val dayLabel: String, val score: Int, val date: String? = null)

/**
 * 언어 지표 하나의 상태. 전체 사용자 기준 "정상 범위"가 아니라 본인의 최근 세션
 * 평균 대비 오늘이 얼마나 벗어났는지(z-score)로 판정한다.
 */
enum class MarkerStatus(val label: String) {
    GOOD("좋음"),
    WATCH("주의"),
    DANGER("위험"),
    INSUFFICIENT_DATA("측정 중")
}

/** 언어 지표 상세 그래프의 점 하나(요일 라벨 + 값). */
data class LinguisticMarkerPoint(val dayLabel: String, val value: Float?)

/** 언어 지표 4개(대명사 비율/명사 비율/어휘 다양성/반복 표현) 중 하나의 UI 표시용 상태. */
data class LinguisticMarkerUi(
    val key: String,
    val displayName: String,
    val status: MarkerStatus,
    val todayValue: Float?,
    val baselineMean: Float?,
    val diffText: String,
    val diffDescription: String,
    val weeklyPoints: List<LinguisticMarkerPoint>
)

/** 그래프 탭. */

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager.from(application)
    private val api   = RetrofitClient.analysis

    /**
     * 보호자가 특정 환자 데이터를 볼 때 설정. null이면 로그인한 본인 userId 사용.
     * [loadForPatient]로 세팅, [resetToSelf]로 초기화.
     */
    private var targetUserId: Int? = null

    private fun resolveUserId(): Int = targetUserId ?: prefs.getUserId()

    /** 보호자 → 환자 분석 조회: userId 세팅 후 전체 리로드 */
    fun loadForPatient(patientId: Int) {
        targetUserId = patientId
        refresh()
    }

    /** 본인 데이터로 복귀 (보호자 화면 벗어날 때 호출) */
    fun resetToSelf() {
        targetUserId = null
    }

    // ── 로딩 상태 ─────────────────────────────────────────────────────────────

    var isLoading by mutableStateOf(false)
        private set

    /** 초기 로드(summary + weekly + streak) 완료 여부 — 스플래시 대기용 */
    var isInitialLoadDone by mutableStateOf(false)
        private set

    /**
     * 분석 화면 진입 시 로딩 화면을 보여줄지 여부.
     * 앱 최초 진입(init) 또는 음성 대화 후 refresh() 시에만 true.
     * 단순 화면 재진입 시에는 false 유지 → 로딩 화면 생략.
     */
    var showLoadingScreen by mutableStateOf(true)
        private set

    var networkError by mutableStateOf<String?>(null)
        private set

    fun clearNetworkError() { networkError = null }

    // ── API 데이터 ─────────────────────────────────────────────────────────────

    private var finalRiskScore by mutableStateOf<Float?>(null)
    private var riskLevel      by mutableStateOf<String?>(null)
    private var speechScore    by mutableStateOf<Float?>(null)
    private var textScore      by mutableStateOf<Float?>(null)
    private var recallScore    by mutableStateOf<Float?>(null)
    private var analyzedAt     by mutableStateOf<String?>(null)

    /** 오늘 날짜 분석 데이터가 실제로 존재하는지 여부 */
    var hasTodayData by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { doLoad() }
    }

    fun refresh() {
        isInitialLoadDone = false
        showLoadingScreen = true
        networkError = null
        viewModelScope.launch { doLoad() }
    }

    fun dismissLoadingScreen() { showLoadingScreen = false }

    private suspend fun doLoad() {
        val userId = resolveUserId()
        if (userId <= 0) { isInitialLoadDone = true; return }
        isLoading = true
        coroutineScope {
            launch { doLoadSummary() }
            launch { doLoadWeeklyScores() }
            launch { doLoadStreak() }
            launch { doLoadLinguisticMarkers() }
        }
        isLoading = false
        isInitialLoadDone = true
    }

    private suspend fun doLoadSummary() {
        val userId = resolveUserId()
        if (userId <= 0) return
        var loaded = false

        runCatching { api.getTodaySummary(userId) }
            .onSuccess { resp ->
                resp.body()?.data?.let { body ->
                    applyBody(body)
                    hasTodayData = true
                    loaded = true
                }
            }
            .onFailure { e -> networkError = toErrorCode(e) }

        if (!loaded && networkError == null) {
            hasTodayData = false
            runCatching { api.getLatestSummary(userId) }
                .onSuccess { resp ->
                    resp.body()?.data?.let { body -> applyBody(body) }
                }
                .onFailure { e -> networkError = toErrorCode(e) }
        }
    }

    private suspend fun doLoadStreak() {
        val userId = resolveUserId()
        if (userId <= 0) return
        runCatching { api.getStreakDays(userId) }
            .onSuccess { resp -> resp.body()?.data?.let { streakDays = it } }
            .onFailure { e -> networkError = toErrorCode(e) }
    }

    // ── 언어 지표(대명사 비율/명사 비율/어휘 다양성/반복 표현) ───────────────────

    var linguisticMarkers by mutableStateOf<List<LinguisticMarkerUi>>(emptyList())
        private set

    /** 어휘 점수 카드에 표시할 종합 상태 — 4개 지표 중 가장 우려스러운 상태를 따른다. */
    val textScoreStatus: MarkerStatus get() {
        if (linguisticMarkers.isEmpty()) return MarkerStatus.INSUFFICIENT_DATA
        return linguisticMarkers.maxByOrNull { markerSeverity(it.status) }?.status
            ?: MarkerStatus.INSUFFICIENT_DATA
    }

    private fun markerSeverity(status: MarkerStatus): Int = when (status) {
        MarkerStatus.INSUFFICIENT_DATA -> -1
        MarkerStatus.GOOD -> 0
        MarkerStatus.WATCH -> 1
        MarkerStatus.DANGER -> 2
    }

    private suspend fun doLoadLinguisticMarkers() {
        val userId = resolveUserId()
        if (userId <= 0) return
        runCatching { api.getLinguisticMarkers(userId) }
            .onSuccess { resp ->
                resp.body()?.data?.let { list ->
                    linguisticMarkers = buildLinguisticMarkerUiList(list)
                }
            }
            .onFailure { e -> networkError = toErrorCode(e) }
    }

    private data class MarkerDef(
        val key: String,
        val displayName: String,
        val increaseIsBad: Boolean,
        val valueOf: (LinguisticMarkerResponse) -> Float?,
        val zScoreOf: (LinguisticMarkerResponse) -> Float?
    )

    private val markerDefs = listOf(
        MarkerDef("pronounNounRatio", "대명사 사용 비율", true,
            { it.pronounNounRatio }, { it.pronounNounRatioZScore }),
        MarkerDef("nounRatio", "명사 사용 비율", false,
            { it.nounRatio }, { it.nounRatioZScore }),
        MarkerDef("lexicalDiversityMattr", "어휘 다양성", false,
            { it.lexicalDiversityMattr }, { it.lexicalDiversityMattrZScore }),
        MarkerDef("repetitionScore", "반복 표현", true,
            { it.repetitionScore }, { it.repetitionScoreZScore })
    )

    /** 최소 3개 과거 세션이 있어야 z-score가 안정적이므로, 그전에는 기준선 없음으로 본다. */
    private fun classifyMarker(zScore: Float?, increaseIsBad: Boolean): MarkerStatus {
        if (zScore == null) return MarkerStatus.INSUFFICIENT_DATA
        val concerningZ = if (increaseIsBad) zScore else -zScore
        return when {
            concerningZ >= 2f -> MarkerStatus.DANGER
            concerningZ >= 1f -> MarkerStatus.WATCH
            else -> MarkerStatus.GOOD
        }
    }

    private fun computeBaselineMean(
        history: List<LinguisticMarkerResponse>,
        valueOf: (LinguisticMarkerResponse) -> Float?
    ): Float? {
        val past = history.drop(1).mapNotNull(valueOf)
        if (past.size < 3) return null
        return (past.sum() / past.size)
    }

    private fun formatDiffText(today: Float?, baselineMean: Float?): String {
        if (today == null || baselineMean == null) return "-"
        val diff = today - baselineMean
        val sign = if (diff >= 0f) "+" else ""
        return "$sign${String.format(Locale.getDefault(), "%.2f", diff)}"
    }

    private fun diffDescription(status: MarkerStatus, key: String): String = when {
        status == MarkerStatus.INSUFFICIENT_DATA -> "기준 데이터를 모으고 있어요"
        status == MarkerStatus.GOOD -> "평소와 비슷한 수준이에요"
        key == "pronounNounRatio" -> "평소보다 대명사 사용이 늘었어요"
        key == "nounRatio" -> "평소보다 명사 사용이 줄었어요"
        key == "lexicalDiversityMattr" -> "평소보다 사용 어휘가 단조로워요"
        key == "repetitionScore" -> "평소보다 같은 말을 반복했어요"
        else -> "평소보다 변화가 있어요"
    }

    private val markerDateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    private fun markerDayLabel(analyzedAt: String?): String {
        if (analyzedAt == null || analyzedAt.length < 19) return ""
        return runCatching {
            val cal = Calendar.getInstance().apply { time = markerDateFmt.parse(analyzedAt.substring(0, 19))!! }
            listOf("일", "월", "화", "수", "목", "금", "토")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }.getOrDefault("")
    }

    private fun buildLinguisticMarkerUiList(
        history: List<LinguisticMarkerResponse>
    ): List<LinguisticMarkerUi> {
        if (history.isEmpty()) return emptyList()

        val today = history.first()
        val recentOldestFirst = history.take(7).reversed()

        return markerDefs.map { def ->
            val zScore = def.zScoreOf(today)
            val status = classifyMarker(zScore, def.increaseIsBad)
            val todayValue = def.valueOf(today)
            val baselineMean = computeBaselineMean(history, def.valueOf)

            LinguisticMarkerUi(
                key = def.key,
                displayName = def.displayName,
                status = status,
                todayValue = todayValue,
                baselineMean = baselineMean,
                diffText = formatDiffText(todayValue, baselineMean),
                diffDescription = diffDescription(status, def.key),
                weeklyPoints = recentOldestFirst.map {
                    LinguisticMarkerPoint(markerDayLabel(it.analyzedAt), def.valueOf(it))
                }
            )
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
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            return (6 downTo 0).map { daysAgo ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
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

    /** 오늘 데이터를 보고 있는지 여부 (선택 날짜가 없거나 오늘이면 true) */
    val isViewingToday: Boolean get() {
        val selDate = selectedDayScore?.date ?: return true
        return parseDate(selDate.substring(0, 10))?.isSameDay(todayCal()) ?: true
    }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun parseDate(s: String): Calendar? = runCatching {
        Calendar.getInstance().also { it.time = dateFmt.parse(s)!! }
    }.getOrNull()
    private fun todayCal(): Calendar = Calendar.getInstance()
    private fun Calendar.isSameDay(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
                get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    private fun riskToHealthScore(score: Float?): Float? =
        score?.let { (100f - it).coerceIn(0f, 100f) }

    private fun displayHealthScore(score: Float?): Int =
        riskToHealthScore(score)?.roundToInt() ?: 0

    private fun formatScore(score: Float?): String =
        score?.roundToInt()?.let { "${it}점" } ?: "-"

    private fun formatHealthScoreFromRisk(score: Float?): String =
        formatScore(riskToHealthScore(score))

    private fun formatMemoryScore(score: Float?): String {
        if (score == null || score <= 0f) return "측정 전"
        return formatScore(score)
    }

    private fun formatRiskLevel(level: String?): String {
        return when (level?.trim()?.uppercase()) {
            "LOW" -> "안정"
            "MEDIUM" -> "주의"
            "HIGH" -> "위험"
            "안정" -> "안정"
            "주의" -> "주의"
            "위험" -> "위험"
            else -> "─"
        }
    }

    /** 헤더에 표시할 종합 점수. 서버 finalRiskScore는 위험도라 앱에서는 건강 점수로 변환한다. */
    val displayScore: Int get() = selectedDayScore
        ?.finalRiskScore?.let { displayHealthScore(it) }
        ?: displayHealthScore(finalRiskScore)

    /** 헤더 배지 — 위험 등급 */
    val displayRiskLevel: String get() =
        formatRiskLevel(selectedDayScore?.riskLevel ?: riskLevel)

    /**
     * 그래프에서 강조할 인덱스.
     * - 날짜 직접 선택 중: 해당 날짜의 인덱스
     * - 오늘 데이터 있음: 마지막 인덱스(오늘)
     * - fallback(latest): latest의 날짜가 그래프 범위 안이면 해당 인덱스, 밖이면 -1(강조 없음)
     */
    val graphHighlightIndex: Int
        get() {
            val dragDate = dragHighlightDate
            if (dragDate != null) {
                val idx = graphPoints.indexOfFirst { it.date?.startsWith(dragDate.take(10)) == true }
                if (idx >= 0) return idx
            }

            val selDate = selectedDayScore?.date
            if (selDate != null) {
                val idx = graphPoints.indexOfFirst { it.date?.startsWith(selDate.take(10)) == true }
                return if (idx >= 0) idx else graphPoints.lastIndex
            }

            if (hasTodayData) return graphPoints.lastIndex

            val latestDate = analyzedAt?.take(10) ?: return -1
            val idx = graphPoints.indexOfFirst { it.date?.startsWith(latestDate) == true }
            return if (idx >= 0) idx else -1
        }

    /** 헤더 날짜 라벨 */
    val displayDateLabel: String
        get() {
            dragPreviewDateLabel?.let { return it }
            if (isViewingToday && hasTodayData) return "오늘의 분석 결과"
            val raw = selectedDayScore?.date ?: analyzedAt ?: return "분석 결과"
            return try {
                val date = parseDate(raw.substring(0, 10)) ?: return "분석 결과"
                val today = todayCal()
                val prefix = if (date.get(Calendar.YEAR) != today.get(Calendar.YEAR)) "${date.get(Calendar.YEAR)}년 " else ""
                "${prefix}${date.get(Calendar.MONTH) + 1}월 ${date.get(Calendar.DAY_OF_MONTH)}일 분석 결과"
            } catch (_: Exception) { "분석 결과" }
        }

    // ── 4 카드 ─────────────────────────────────────────────────────────────────

    val todayItems: List<AnalysisItem>
        get() {
            val sel = selectedDayScore
            val dRisk       = sel?.riskLevel   ?: riskLevel
            val dSpeechRisk = sel?.speechScore ?: speechScore
            val dTextScore  = sel?.textScore   ?: textScore

            return listOf(
                AnalysisItem(
                    icon      = Icons.Default.BarChart,
                    label     = "종합 위험도",
                    valueText = formatRiskLevel(dRisk),
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                ),
                AnalysisItem(
                    icon      = Icons.Default.RecordVoiceOver,
                    label     = "음성 점수",
                    valueText = formatHealthScoreFromRisk(dSpeechRisk),
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                ),
                AnalysisItem(
                    icon      = Icons.Default.Psychology,
                    label     = "기억력 점수",
                    valueText = formatMemoryScore(dTextScore),
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                ),
                AnalysisItem(
                    icon      = Icons.AutoMirrored.Filled.MenuBook,
                    label     = "어휘 점수",
                    valueText = formatScore(dTextScore),
                    trendText = "",
                    trend     = AnalysisItem.Trend.Steady
                )
            )
        }

    // ── 주간 그래프 ───────────────────────────────────────────────────────────

    var graphPoints by mutableStateOf<List<DailyScore>>(emptyList())
        private set

    var streakDays by mutableIntStateOf(0)
        private set

    var selectedDayScore by mutableStateOf<DailyScoreResponse?>(null)
        private set

    var isDayLoading by mutableStateOf(false)
        private set

    private val dateFmtVm = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun parseDateVm(s: String): Calendar? = runCatching {
        Calendar.getInstance().also { it.time = dateFmtVm.parse(s)!! }
    }.getOrNull()

    private suspend fun doLoadWeeklyScores() {
        val userId = resolveUserId()
        if (userId <= 0) return

        runCatching { api.getWeeklyScores(userId) }
            .onSuccess { resp ->
                resp.body()?.data?.let { list ->
                    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
                    graphPoints = list.map { d ->
                        val cal = d.date?.let { parseDateVm(it.take(10)) }
                        val label = cal?.let { dayNames[it.get(Calendar.DAY_OF_WEEK) - 1] } ?: ""

                        DailyScore(
                            dayLabel = label,
                            score    = displayHealthScore(d.finalRiskScore),
                            date     = d.date
                        )
                    }
                }
            }
            .onFailure { e -> networkError = toErrorCode(e) }
    }

    private fun toErrorCode(e: Throwable): String = when (e) {
        is HttpException -> e.code().toString()
        is java.net.SocketTimeoutException -> "TIMEOUT"
        is IOException -> "NETWORK_ERROR"
        else -> "UNKNOWN"
    }

    fun onGraphPointTapped(dateStr: String?) {
        val userId = resolveUserId()
        if (userId <= 0 || dateStr == null) return

        viewModelScope.launch {
            isDayLoading = true
            runCatching { api.getDailyScore(userId, dateStr) }
                .onSuccess { resp -> selectedDayScore = resp.body()?.data }
            isDayLoading = false
        }
    }

    fun clearSelectedDay() {
        selectedDayScore = null
    }

    // ── 그래프 롱프레스 드래그 ─────────────────────────────────────────────────

    /** 현재 그래프를 롱프레스+드래그 중인지 여부 */
    var isDraggingGraph by mutableStateOf(false)
        private set

    /** 드래그 중 헤더에 미리 보여줄 날짜 문자열 (점수는 바뀌지 않음) */
    var dragPreviewDateLabel by mutableStateOf<String?>(null)
        private set

    var dragHighlightDate by mutableStateOf<String?>(null)
        private set

    fun onGraphDragStart() {
        isDraggingGraph = true
    }

    fun onGraphDragMove(dateStr: String?) {
        dragHighlightDate = dateStr
        dragPreviewDateLabel = dateStr?.let { formatDragLabel(it) }
    }

    fun onGraphDragEnd(dateStr: String?) {
        isDraggingGraph = false
        dragHighlightDate = null
        dragPreviewDateLabel = null
        onGraphPointTapped(dateStr)
    }

    fun onGraphDragCancel() {
        isDraggingGraph = false
        dragHighlightDate = null
        dragPreviewDateLabel = null
    }

    private fun formatDragLabel(dateStr: String): String {
        return try {
            val d = parseDateVm(dateStr.take(10)) ?: return "분석 결과"
            val today = Calendar.getInstance()
            val y = d.get(Calendar.YEAR)
            val m = d.get(Calendar.MONTH) + 1
            val day = d.get(Calendar.DAY_OF_MONTH)

            when {
                d.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        d.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "오늘의 분석 결과"

                y != today.get(Calendar.YEAR) -> "${y}년 ${m}월 ${day}일 분석 결과"

                else -> "${m}월 ${day}일 분석 결과"
            }
        } catch (_: Exception) {
            "분석 결과"
        }
    }
}