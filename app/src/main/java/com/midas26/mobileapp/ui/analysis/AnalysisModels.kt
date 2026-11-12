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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** 분석 화면의 점수 카드에 표시할 데이터. */
data class AnalysisItem(
    val icon: ImageVector,
    val label: String,
    val valueText: String,
    val trendText: String,
    val trend: Trend
) {
    enum class Trend { Steady }
}

/** 날짜별 인지 건강 점수. 값이 높을수록 양호하다. */
data class DailyScore(val dayLabel: String, val score: Int, val date: String? = null)

/** 개인의 과거 분석 기록 대비 변화량(z-score)에 따른 언어 지표 상태. */
enum class MarkerStatus(val label: String) {
    GOOD("좋음"),
    WATCH("주의"),
    DANGER("위험"),
    INSUFFICIENT_DATA("측정 중")
}

/** 언어 지표 그래프의 날짜별 측정값. */
data class LinguisticMarkerPoint(val dayLabel: String, val value: Float?)

/** 언어 지표별 상태, 기준값 및 최근 측정 기록. */
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

/** 분석 결과와 주간 추이를 관리하는 ViewModel. */
class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PrefsManager.from(application)
    private val api   = RetrofitClient.analysis

    // null이면 로그인한 사용자, 값이 있으면 보호자가 선택한 사용자를 조회한다.
    private var targetUserId: Int? = null

    // 튜토리얼에서는 서버 데이터 대신 메모리의 예시 데이터를 표시한다.
    var isTutorialDemo by mutableStateOf(false)
        private set

    private var demoSelectedIndex by mutableIntStateOf(6)
    private var loadJob: Job? = null
    private var dayLoadJob: Job? = null

    private val demoScores = listOf(78, 80, 79, 83, 82, 84, 85)

    private val demoGraphPoints: List<DailyScore>
        get() {
            val days = listOf("일", "월", "화", "수", "목", "금", "토")
            return demoScores.mapIndexed { index, score ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, index - 6) }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                DailyScore(days[cal.get(Calendar.DAY_OF_WEEK) - 1], score, date)
            }
        }

    /** 진행 중인 조회를 중단하고 튜토리얼 예시 데이터로 전환한다. */
    fun startTutorialDemo() {
        loadJob?.cancel()
        dayLoadJob?.cancel()
        isTutorialDemo = true
        demoSelectedIndex = 6
        isLoading = false
        isDayLoading = false
        isInitialLoadDone = true
        showLoadingScreen = false
        onGraphDragCancel()
    }

    /** 튜토리얼 예시 표시를 종료하고 그래프 선택 상태를 초기화한다. */
    fun stopTutorialDemo() {
        if (!isTutorialDemo) return
        isTutorialDemo = false
        demoSelectedIndex = 6
        selectedDayScore = null
        onGraphDragCancel()

    }

    private fun resolveUserId(): Int = targetUserId ?: prefs.getUserId()

    /** 보호자가 선택한 사용자의 분석 결과를 다시 조회한다. */
    fun loadForPatient(patientId: Int) {
        stopTutorialDemo()
        dayLoadJob?.cancel()
        selectedDayScore = null
        targetUserId = patientId
        refresh()
    }

    /** 조회 대상을 로그인한 사용자로 되돌린다. */
    fun resetToSelf() {
        dayLoadJob?.cancel()
        selectedDayScore = null
        targetUserId = null
    }

    // 데이터 조회 및 로딩 화면 상태

    var isLoading by mutableStateOf(false)
        private set

    // 초기 분석 데이터 조회 완료 여부
    var isInitialLoadDone by mutableStateOf(false)
        private set


    // 최초 진입과 새로고침 시 로딩 화면을 표시한다.
    var showLoadingScreen by mutableStateOf(true)
        private set

    var networkError by mutableStateOf<String?>(null)
        private set

    fun clearNetworkError() { networkError = null }

    // 서버에서 수신한 분석 결과
    private var finalRiskScore by mutableStateOf<Float?>(null)
    private var riskLevel      by mutableStateOf<String?>(null)
    private var speechScore    by mutableStateOf<Float?>(null)
    private var textScore      by mutableStateOf<Float?>(null)
    private var recallScore    by mutableStateOf<Float?>(null)
    private var analyzedAt     by mutableStateOf<String?>(null)

    // 오늘 날짜에 해당하는 분석 결과 존재 여부
    var hasTodayData by mutableStateOf(false)
        private set

    init {
        loadJob = viewModelScope.launch { doLoad() }
    }

    fun refresh() {
        if (isTutorialDemo) return
        loadJob?.cancel()
        dayLoadJob?.cancel()
        selectedDayScore = null
        hasTodayData = false
        isInitialLoadDone = false
        showLoadingScreen = true
        networkError = null
        loadJob = viewModelScope.launch { doLoad() }
    }

    fun dismissLoadingScreen() { showLoadingScreen = false }

    private suspend fun doLoad() {
        if (isTutorialDemo) return
        val userId = resolveUserId()
        if (userId <= 0) {
            isLoading = false
            isInitialLoadDone = true
            showLoadingScreen = false
            return
        }
        isLoading = true
        try {
            coroutineScope {
                launch { doLoadSummary() }
                launch { doLoadWeeklyScores() }
                launch { doLoadStreak() }
                launch { doLoadLinguisticMarkers() }
            }
        } finally {
            isLoading = false
            if (!isTutorialDemo) isInitialLoadDone = true
        }
    }

    private suspend fun doLoadSummary() {
        val userId = resolveUserId()
        if (userId <= 0 || isTutorialDemo) return
        var loaded = false

        runCatching { api.getTodaySummary(userId) }
            .onSuccess { resp ->
                if (isTutorialDemo) return@onSuccess
                resp.body()?.data?.let { body ->
                    applyBody(body)
                    hasTodayData = true
                    loaded = true
                }
            }
            .onFailure { e -> if (e is CancellationException) throw e
                if (!isTutorialDemo) networkError = toErrorCode(e) }

        if (!loaded && networkError == null) {
            hasTodayData = false
            runCatching { api.getLatestSummary(userId) }
                .onSuccess { resp ->
                    if (isTutorialDemo) return@onSuccess
                    resp.body()?.data?.let { body -> applyBody(body) }
                }
                .onFailure { e -> if (e is CancellationException) throw e
                    if (!isTutorialDemo) networkError = toErrorCode(e) }
        }
    }

    private suspend fun doLoadStreak() {
        val userId = resolveUserId()
        if (userId <= 0 || isTutorialDemo) return
        runCatching { api.getStreakDays(userId) }
            .onSuccess { resp -> if (!isTutorialDemo) resp.body()?.data?.let { streakDays = it } }
            .onFailure { e -> if (e is CancellationException) throw e
                if (!isTutorialDemo) networkError = toErrorCode(e) }
    }

    // 언어 지표: 대명사·명사 사용, 어휘 다양성, 반복 표현

    private var realLinguisticMarkers by mutableStateOf<List<LinguisticMarkerUi>>(emptyList())

    private val demoLinguisticMarkers: List<LinguisticMarkerUi>
        get() = listOf(
            Triple("pronounNounRatio", "대명사 사용 비율", 0.18f),
            Triple("nounRatio", "명사 사용 비율", 0.42f),
            Triple("lexicalDiversityMattr", "어휘 다양성", 0.71f),
            Triple("repetitionScore", "반복 표현", 0.12f)
        ).map { (key, name, value) ->
            LinguisticMarkerUi(
                key = key,
                displayName = name,
                status = MarkerStatus.GOOD,
                todayValue = value,
                baselineMean = value,
                diffText = "+0.00",
                diffDescription = "튜토리얼 예시 데이터입니다",
                weeklyPoints = demoGraphPoints.map { LinguisticMarkerPoint(it.dayLabel, value) }
            )
        }

    var linguisticMarkers: List<LinguisticMarkerUi>
        get() = if (isTutorialDemo) demoLinguisticMarkers else realLinguisticMarkers
        private set(value) { realLinguisticMarkers = value }

    // 언어 지표 중 가장 높은 주의 수준을 대표 상태로 사용한다.
    val textScoreStatus: MarkerStatus get() {
        if (isTutorialDemo) return MarkerStatus.GOOD
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
        if (userId <= 0 || isTutorialDemo) return
        runCatching { api.getLinguisticMarkers(userId) }
            .onSuccess { resp ->
                if (isTutorialDemo) return@onSuccess
                resp.body()?.data?.let { list ->
                    linguisticMarkers = buildLinguisticMarkerUiList(list)
                }
            }
            .onFailure { e -> if (e is CancellationException) throw e
                if (!isTutorialDemo) networkError = toErrorCode(e) }
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

    // 기준 데이터가 없으면 측정 중 상태로 표시한다.
    private fun classifyMarker(zScore: Float?, increaseIsBad: Boolean): MarkerStatus {
        if (zScore == null || !zScore.isFinite()) return MarkerStatus.INSUFFICIENT_DATA
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
        val past = history.drop(1).mapNotNull(valueOf).filter { it.isFinite() }
        if (past.size < 3) return null
        return (past.sum() / past.size)
    }

    private fun formatDiffText(today: Float?, baselineMean: Float?): String {
        if (today == null || baselineMean == null || !today.isFinite() || !baselineMean.isFinite()) return "-"
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

    // 최근 7일 분석 기록 유무(0: 6일 전, 6: 오늘)
    val weeklyChecks: List<Boolean>
        get() = if (graphPoints.size == 7) {
            graphPoints.map { it.score > 0 }
        } else {
            List(7) { false }
        }

    // 최근 7일 요일 라벨(0: 6일 전, 6: 오늘)
    val weeklyDayLabels: List<String>
        get() {
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            return (6 downTo 0).map { daysAgo ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
            }
        }

    // 오늘은 주간 목록의 마지막 인덱스다.
    val todayDayIndex: Int get() = 6

    private fun applyBody(body: com.midas26.mobileapp.network.SessionSummaryResponse) {
        finalRiskScore = body.finalRiskScore
        riskLevel      = body.riskLevel
        speechScore    = body.speechScore
        textScore      = body.textScore
        recallScore    = body.recallScore
        analyzedAt     = body.analyzedAt
    }

    // 분석 결과 헤더

    // 선택된 날짜가 없거나 오늘이면 true
    val isViewingToday: Boolean get() {
        if (isTutorialDemo) return demoSelectedIndex == 6
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
        score?.takeIf { it.isFinite() }?.let { (100f - it).coerceIn(0f, 100f) }

    private fun displayHealthScore(score: Float?): Int =
        riskToHealthScore(score)?.roundToInt() ?: 0

    private fun formatScore(score: Float?): String =
        score?.takeIf { it.isFinite() }?.roundToInt()?.let { "${it}점" } ?: "-"

    private fun formatHealthScoreFromRisk(score: Float?): String =
        formatScore(riskToHealthScore(score))

    private fun formatMemoryScore(score: Float?): String {
        if (score == null || !score.isFinite() || score <= 0f) return "측정 전"
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

    // 서버 위험도를 건강 점수(100 - 위험도)로 변환한다.
    val displayScore: Int get() = if (isTutorialDemo) demoScores[demoSelectedIndex]
    else selectedDayScore?.finalRiskScore?.let { displayHealthScore(it) }
        ?: displayHealthScore(finalRiskScore)

    // 분석 결과 헤더 배지 — 위험 등급
    val displayRiskLevel: String get() =
        if (isTutorialDemo) "안정" else formatRiskLevel(selectedDayScore?.riskLevel ?: riskLevel)

    // 드래그·선택 날짜를 우선 강조하고, 없으면 오늘 또는 최신 기록을 사용한다.
    val graphHighlightIndex: Int
        get() {
            if (isTutorialDemo) {
                val dragged = dragHighlightDate?.let { date ->
                    demoGraphPoints.indexOfFirst { it.date == date.take(10) }
                } ?: -1
                return if (dragged >= 0) dragged else demoSelectedIndex
            }
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

    // 분석 결과 헤더 날짜 라벨
    val displayDateLabel: String
        get() {
            if (isTutorialDemo) {
                val date = demoGraphPoints[demoSelectedIndex].date.orEmpty()
                return if (demoSelectedIndex == 6) "오늘의 분석 결과" else formatDragLabel(date)
            }
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

    // 음성·기억력·어휘 분석 카드에 사용할 데이터

    val todayItems: List<AnalysisItem>
        get() {
            if (isTutorialDemo) return listOf(
                AnalysisItem(Icons.Default.BarChart, "종합 위험도", "안정", "", AnalysisItem.Trend.Steady),
                AnalysisItem(Icons.Default.RecordVoiceOver, "음성 점수", "88점", "", AnalysisItem.Trend.Steady),
                AnalysisItem(Icons.Default.Psychology, "기억력 점수", "82점", "", AnalysisItem.Trend.Steady),
                AnalysisItem(Icons.AutoMirrored.Filled.MenuBook, "어휘 점수", "86점", "", AnalysisItem.Trend.Steady)
            )
            val sel = selectedDayScore
            val dRisk       = sel?.riskLevel   ?: riskLevel
            val dSpeechRisk = sel?.speechScore ?: speechScore
            val dTextScore  = sel?.textScore   ?: textScore
            val dRecallScore = recallScore

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
                    valueText = formatMemoryScore(dRecallScore),
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

    // 주간 그래프 데이터 및 날짜별 조회

    private var realGraphPoints by mutableStateOf<List<DailyScore>>(emptyList())

    var graphPoints: List<DailyScore>
        get() = if (isTutorialDemo) demoGraphPoints else realGraphPoints
        private set(value) { realGraphPoints = value }

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
        if (userId <= 0 || isTutorialDemo) return

        runCatching { api.getWeeklyScores(userId) }
            .onSuccess { resp ->
                if (isTutorialDemo) return@onSuccess
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
            .onFailure { e -> if (e is CancellationException) throw e
                if (!isTutorialDemo) networkError = toErrorCode(e) }
    }

    private fun toErrorCode(e: Throwable): String = when (e) {
        is HttpException -> e.code().toString()
        is java.net.SocketTimeoutException -> "TIMEOUT"
        is IOException -> "NETWORK_ERROR"
        else -> "UNKNOWN"
    }

    fun onGraphPointTapped(dateStr: String?) {
        if (isTutorialDemo) {
            val index = demoGraphPoints.indexOfFirst { it.date == dateStr?.take(10) }
            if (index >= 0) demoSelectedIndex = index
            return
        }
        val userId = resolveUserId()
        if (userId <= 0 || dateStr == null) return

        dayLoadJob?.cancel()
        dayLoadJob = viewModelScope.launch {
            isDayLoading = true
            try {
                val response = api.getDailyScore(userId, dateStr)
                if (!isTutorialDemo && resolveUserId() == userId) {
                    selectedDayScore = response.body()?.data
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isTutorialDemo) networkError = toErrorCode(e)
            } finally {
                isDayLoading = false
            }
        }
    }

    fun clearSelectedDay() {
        if (isTutorialDemo) {
            demoSelectedIndex = 6
            return
        }
        dayLoadJob?.cancel()
        selectedDayScore = null
    }

    // 그래프 길게 누르기 및 드래그

    /** 그래프 드래그 진행 여부. */
    var isDraggingGraph by mutableStateOf(false)
        private set

    /** 드래그 중 헤더에 표시할 날짜(점수는 확정 시 변경). */
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
