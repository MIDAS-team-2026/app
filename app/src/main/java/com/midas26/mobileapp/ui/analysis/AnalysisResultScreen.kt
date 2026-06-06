package com.midas26.mobileapp.ui.analysis

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.AppColor.textTertiary
import com.midas26.mobileapp.ui.theme.AppColor.textSecondary
import com.midas26.mobileapp.ui.theme.AppColor.greenPrimary
import com.midas26.mobileapp.ui.theme.AppColor.greenSecondary
import com.midas26.mobileapp.ui.theme.AppColor.accentDark
import com.midas26.mobileapp.ui.theme.AppColor.guardianPrimary
import com.midas26.mobileapp.ui.theme.AppColor.guardianDark
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.theme.LocalHapticEnabled
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class GuardianUser(
    val id: Int,
    val name: String,
    val relation: String
)

private data class GuardianMetric(
    val icon: ImageVector,
    val label: String,
    val value: String
)

private data class GuardianDailyScore(
    val dayLabel: String,
    val score: Int
)

private data class GuardianDummyAnalysis(
    val user: GuardianUser,
    val score: Int,
    val riskLevel: String,
    val summary: String,
    val weeklyScores: List<GuardianDailyScore>,
    val metrics: List<GuardianMetric>
)

private val guardianDummyAnalyses = listOf(
    GuardianDummyAnalysis(
        user = GuardianUser(1, "홍길동", "부"),
        score = 87,
        riskLevel = "정상",
        summary = "전반적으로 안정적인 인지 상태를 유지하고 있습니다.",
        weeklyScores = listOf(
            GuardianDailyScore("월", 82),
            GuardianDailyScore("화", 84),
            GuardianDailyScore("수", 85),
            GuardianDailyScore("목", 83),
            GuardianDailyScore("금", 86),
            GuardianDailyScore("토", 88),
            GuardianDailyScore("일", 87)
        ),
        metrics = listOf(
            GuardianMetric(Icons.Filled.Psychology, "기억 일치도", "90%"),
            GuardianMetric(Icons.Filled.RecordVoiceOver, "어휘 다양도", "85%"),
            GuardianMetric(Icons.Filled.Replay, "반복 표현", "낮음"),
            GuardianMetric(Icons.Filled.Timer, "응답 지연", "양호")
        )
    ),
    GuardianDummyAnalysis(
        user = GuardianUser(2, "박순임", "모"),
        score = 63,
        riskLevel = "주의",
        summary = "최근 기억 일치도와 응답 속도에서 주의가 필요한 변화가 관찰됩니다.",
        weeklyScores = listOf(
            GuardianDailyScore("월", 70),
            GuardianDailyScore("화", 68),
            GuardianDailyScore("수", 65),
            GuardianDailyScore("목", 66),
            GuardianDailyScore("금", 64),
            GuardianDailyScore("토", 62),
            GuardianDailyScore("일", 63)
        ),
        metrics = listOf(
            GuardianMetric(Icons.Filled.Psychology, "기억 일치도", "60%"),
            GuardianMetric(Icons.Filled.RecordVoiceOver, "어휘 다양도", "68%"),
            GuardianMetric(Icons.Filled.Replay, "반복 표현", "다소 높음"),
            GuardianMetric(Icons.Filled.Timer, "응답 지연", "주의")
        )
    )
)

@Composable
fun AnalysisResultScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    val context = LocalContext.current
    val isGuardian = PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN

    var selectedGuardianUserId by remember { mutableStateOf<Int?>(null) }

    if (isGuardian && selectedGuardianUserId == null) {
        GuardianAnalysisUserSelectScreen(
            onBack = onBack,
            onUserClick = { selectedGuardianUserId = it }
        )
        return
    }

    if (isGuardian && selectedGuardianUserId != null) {
        val analysis = guardianDummyAnalyses.firstOrNull {
            it.user.id == selectedGuardianUserId
        }

        if (analysis != null) {
            GuardianAnalysisDetailScreen(
                analysis = analysis,
                onBack = { selectedGuardianUserId = null }
            )
        }
        return
    }

    UserAnalysisResultContent(
        onBack = onBack,
        viewModel = viewModel
    )
}

@Composable
private fun GuardianAnalysisUserSelectScreen(
    onBack: () -> Unit,
    onUserClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = AppColor.textPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "📊 분석 결과 확인",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFEFF4FF)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋",
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.size(12.dp))

                Text(
                    text = "분석 결과를 확인할 사용자를 선택하세요",
                    fontSize = 20.sp,
                    color = AppColor.guardianDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        guardianDummyAnalyses.forEach { analysis ->
            GuardianUserCard(
                user = analysis.user,
                onClick = { onUserClick(analysis.user.id) }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GuardianUserCard(
    user: GuardianUser,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, Color(0xFFB8D9FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${user.name} (${user.relation})",
                fontSize = 30.sp,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = "선택",
                tint = Color(0xFF9AA3AF),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun GuardianAnalysisDetailScreen(
    analysis: GuardianDummyAnalysis,
    onBack: () -> Unit
) {
    val fontScale = LocalFontSizeScale.current.scale

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColor.guardianDark,
                            AppColor.guardianPrimary
                        )
                    )
                )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "사용자 선택으로 돌아가기",
                            tint = BrandWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "${analysis.user.name}님 분석 결과",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                ) {
                    Text(
                        text = "오늘의 인지 점수",
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrandWhite.copy(alpha = 0.92f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = analysis.score.toString(),
                            fontSize = (56 * fontScale).sp,
                            color = BrandWhite,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.size(4.dp))

                        Text(
                            text = "점",
                            fontSize = (18 * fontScale).sp,
                            color = BrandWhite,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Spacer(modifier = Modifier.size(12.dp))

                        Surface(
                            shape = CircleShape,
                            color = BrandWhite,
                            modifier = Modifier.padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = analysis.riskLevel,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColor.guardianDark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = analysis.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.95f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite,
                border = BorderStroke(1.5.dp, AppColor.divider)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "이번 주 추이",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColor.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "최근 7일 더미 분석 데이터",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.textTertiary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    GuardianWeeklyLineChart(
                        points = analysis.weeklyScores,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GuardianMetricCard(
                    metric = analysis.metrics[0],
                    modifier = Modifier.weight(1f)
                )

                GuardianMetricCard(
                    metric = analysis.metrics[1],
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GuardianMetricCard(
                    metric = analysis.metrics[2],
                    modifier = Modifier.weight(1f)
                )

                GuardianMetricCard(
                    metric = analysis.metrics[3],
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GuardianMetricCard(
    metric: GuardianMetric,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontSizeScale.current.scale

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = metric.icon,
                    contentDescription = null,
                    tint = AppColor.textTertiary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = metric.value,
                fontSize = (24 * fontScale).sp,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GuardianWeeklyLineChart(
    points: List<GuardianDailyScore>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val pad = 16f

            val scores = points.map { it.score }
            val minScore = scores.minOf { it }.toFloat() - 4f
            val maxScore = scores.maxOf { it }.toFloat() + 4f
            val span = (maxScore - minScore).coerceAtLeast(1f)

            val xs = points.indices.map { i ->
                pad + (w - 2 * pad) * i / (points.size - 1).coerceAtLeast(1)
            }

            val ys = scores.map { score ->
                h - pad - (h - 2 * pad) * (score - minScore) / span
            }

            val areaPath = Path().apply {
                moveTo(xs.first(), h - pad)
                lineTo(xs.first(), ys.first())

                for (i in 1 until xs.size) {
                    val cx = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }

                lineTo(xs.last(), h - pad)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppColor.guardianPrimary.copy(alpha = 0.35f),
                        AppColor.guardianPrimary.copy(alpha = 0f)
                    )
                )
            )

            val linePath = Path().apply {
                moveTo(xs.first(), ys.first())

                for (i in 1 until xs.size) {
                    val cx = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }
            }

            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        AppColor.guardianPrimary,
                        AppColor.guardianDark
                    ),
                    startX = 0f,
                    endX = w
                ),
                style = Stroke(width = 8f)
            )

            for (i in points.indices) {
                drawCircle(
                    color = BrandWhite,
                    radius = 18f,
                    center = Offset(xs[i], ys[i])
                )

                drawCircle(
                    color = AppColor.guardianDark,
                    radius = 11f,
                    center = Offset(xs[i], ys[i])
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { point ->
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }
        }
    }
}

@Composable
private fun UserAnalysisResultContent(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel
) {
    val fontScale = LocalFontSizeScale.current.scale

    var minTimeElapsed by remember { mutableStateOf(!viewModel.showLoadingScreen) }

    LaunchedEffect(viewModel.showLoadingScreen) {
        if (viewModel.showLoadingScreen) {
            minTimeElapsed = false
            delay(2000)
            minTimeElapsed = true
        }
    }

    val showLoading = viewModel.showLoadingScreen && (viewModel.isLoading || !minTimeElapsed)

    LaunchedEffect(showLoading) {
        if (!showLoading && viewModel.showLoadingScreen) {
            viewModel.dismissLoadingScreen()
        }
    }

    Crossfade(
        targetState = showLoading,
        animationSpec = tween(durationMillis = 500),
        label = "analysis_crossfade"
    ) { loading ->
        if (loading) {
            AnalysisLoadingScreen(isLoading = viewModel.isLoading, onFinished = {})
            return@Crossfade
        }

        val isDragging = viewModel.isDraggingGraph

        val scrimAlpha by animateFloatAsState(
            targetValue = if (isDragging) 0.52f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "scrim"
        )

        val dateLabelScale by animateFloatAsState(
            targetValue = if (isDragging) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "date_scale"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandWhite)
        ) {
            val isToday = viewModel.isViewingToday && viewModel.hasTodayData

            val animSpec = tween<Color>(durationMillis = 400)

            val topColor by animateColorAsState(
                targetValue = if (isGuardian) AppColor.guardianDark
                              else if (isToday) AppColor.accentDark else AppColor.textSecondary,
                animationSpec = animSpec,
                label = "top_color"
            )

            val botColor by animateColorAsState(
                targetValue = if (isGuardian) AppColor.guardianPrimary
                              else if (isToday) AppColor.greenPrimary else AppColor.textTertiary,
                animationSpec = animSpec,
                label = "bottom_color"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(topColor, botColor)
                        )
                    )
            ) {
                Column(modifier = Modifier.wrapContentHeight()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (!isToday) viewModel.clearSelectedDay()
                                else onBack()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isToday) "뒤로가기" else "오늘로 돌아가기",
                                tint = BrandWhite,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "분석 결과",
                            style = MaterialTheme.typography.titleLarge,
                            color = BrandWhite,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        )

                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                    ) {
                        Text(
                            text = viewModel.displayDateLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Transparent
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = viewModel.displayScore.toString(),
                                fontSize = (56 * fontScale).sp,
                                color = BrandWhite,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.size(4.dp))

                            Text(
                                text = "점",
                                fontSize = (18 * fontScale).sp,
                                color = BrandWhite,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Spacer(modifier = Modifier.size(12.dp))

                            Surface(
                                shape = CircleShape,
                                color = BrandWhite,
                                modifier = Modifier.padding(bottom = 14.dp)
                            ) {
                                Text(
                                    text = viewModel.displayRiskLevel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isToday) AppColor.accentDark else AppColor.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )

                Text(
                    text = viewModel.displayDateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrandWhite.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = (28 + 16 + 56 * fontScale).dp)
                        .scale(dateLabelScale)
                )
            }

            var graphCardHeightPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val graphCardHeightDp = with(density) { graphCardHeightPx.toDp() }

            Box(modifier = Modifier.fillMaxSize()) {
                val items = viewModel.todayItems

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp + graphCardHeightDp)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.weight(2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ItemCard(item = items[0], modifier = Modifier.weight(1f))
                        ItemCard(item = items[1], modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ItemCard(item = items[2], modifier = Modifier.weight(1f))
                        ItemCard(item = items[3], modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.weight(2f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                        .onGloballyPositioned { coords ->
                            graphCardHeightPx = coords.size.height
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = BrandWhite,
                    border = BorderStroke(1.5.dp, AppColor.divider)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "이번 주 추이",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColor.textPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "최근 7일 · 길게 눌러 날짜 탐색",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColor.textTertiary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        WeeklyLineChart(
                            points = viewModel.graphPoints,
                            highlightIndex = viewModel.graphHighlightIndex,
                            onPointTapped = { date -> viewModel.onGraphPointTapped(date) },
                            onDragStart = { viewModel.onGraphDragStart() },
                            onDragMove = { date -> viewModel.onGraphDragMove(date) },
                            onDragEnd = { date -> viewModel.onGraphDragEnd(date) },
                            onDragCancel = { viewModel.onGraphDragCancel() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyLineChart(
    points: List<DailyScore>,
    highlightIndex: Int = points.lastIndex,
    onPointTapped: (String?) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragMove: (String?) -> Unit = {},
    onDragEnd: (String?) -> Unit = {},
    onDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticEnabled.current

    val hasData = points.map { it.score > 0 }

    val filledScores = run {
        val result = points.map { it.score }.toMutableList()

        for (i in result.indices) {
            if (result[i] == 0) {
                result[i] = if (i > 0) {
                    result[i - 1]
                } else {
                    result.firstOrNull { it > 0 } ?: 0
                }
            }
        }

        result
    }

    val animatedHighlight by animateFloatAsState(
        targetValue = if (highlightIndex >= 0) highlightIndex.toFloat() else -999f,
        animationSpec = tween(durationMillis = 300),
        label = "highlight"
    )

    var computedXs = remember { listOf<Float>() }
    var lastDragIdx = remember { -1 }

    fun nearestIdx(offsetX: Float): Int {
        if (computedXs.isEmpty()) return -1

        return computedXs.indices.minByOrNull {
            abs(computedXs[it] - offsetX)
        }?.takeIf {
            hasData[it]
        } ?: -1
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .pointerInput(points) {
                    val longPressMs = viewConfiguration.longPressTimeoutMillis

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        lastDragIdx = -1

                        var isDragging = false
                        val downTime = System.currentTimeMillis()
                        var longPressTriggered = false
                        var endDate: String? = null

                        while (true) {
                            val ev = awaitPointerEvent()
                            val pos = ev.changes.firstOrNull()?.position ?: break
                            val elapsed = System.currentTimeMillis() - downTime

                            if (!longPressTriggered) {
                                if (elapsed >= longPressMs) {
                                    longPressTriggered = true
                                    isDragging = true

                                    onDragStart()

                                    val idx = nearestIdx(pos.x)

                                    if (idx >= 0) {
                                        lastDragIdx = idx
                                        endDate = points[idx].date
                                        onDragMove(endDate)

                                        if (hapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            } else {
                                val idx = nearestIdx(pos.x)

                                if (idx >= 0 && idx != lastDragIdx) {
                                    lastDragIdx = idx
                                    endDate = points[idx].date
                                    onDragMove(endDate)

                                    if (hapticEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }

                            ev.changes.forEach { it.consume() }

                            if (ev.changes.none { it.pressed }) {
                                if (isDragging) {
                                    onDragEnd(endDate)
                                } else {
                                    val idx = nearestIdx(down.position.x)

                                    if (idx >= 0) {
                                        onPointTapped(points[idx].date)

                                        if (hapticEnabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }

                                break
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pad = 16f

            val minScore = filledScores.minOf { it }.toFloat() - 4f
            val maxScore = filledScores.maxOf { it }.toFloat() + 4f
            val span = (maxScore - minScore).coerceAtLeast(1f)

            val xs = points.indices.map { i ->
                pad + (w - 2 * pad) * i / (points.size - 1).coerceAtLeast(1)
            }

            computedXs = xs

            val ys = filledScores.map { score ->
                h - pad - (h - 2 * pad) * (score - minScore) / span
            }

            val areaPath = Path().apply {
                moveTo(xs.first(), h - pad)
                lineTo(xs.first(), ys.first())

                for (i in 1 until xs.size) {
                    val cx = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }

                lineTo(xs.last(), h - pad)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppColor.greenPrimary.copy(alpha = 0.35f),
                        AppColor.greenPrimary.copy(alpha = 0f)
                    )
                )
            )

            val linePath = Path().apply {
                moveTo(xs.first(), ys.first())

                for (i in 1 until xs.size) {
                    val cx = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
                }
            }

            drawPath(
                path = linePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(AppColor.greenPrimary, AppColor.accentDark),
                    startX = 0f,
                    endX = w
                ),
                style = Stroke(width = 8f)
            )

            for (i in points.indices) {
                val fraction = (1f - abs(i - animatedHighlight)).coerceIn(0f, 1f)

                val dotColor = if (hasData[i]) {
                    if (fraction > 0.5f) AppColor.greenSecondary else AppColor.accentDark
                } else {
                    AppColor.textTertiary
                }

                val outerRadius = lerp(12f, 28f, fraction)
                val innerRadius = lerp(8f, 19f, fraction)

                drawCircle(
                    color = BrandWhite,
                    radius = outerRadius,
                    center = Offset(xs[i], ys[i])
                )

                drawCircle(
                    color = dotColor,
                    radius = innerRadius,
                    center = Offset(xs[i], ys[i])
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { i, point ->
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasData[i]) AppColor.textTertiary else AppColor.textTertiary
                )
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: AnalysisItem,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalFontSizeScale.current.scale

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = AppColor.textTertiary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.valueText,
                fontSize = (26 * fontScale).sp,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}