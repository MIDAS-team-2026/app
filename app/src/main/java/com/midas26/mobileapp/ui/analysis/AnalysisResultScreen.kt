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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
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
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.theme.LocalHapticEnabled
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay
import kotlin.math.abs


@Composable
fun AnalysisResultScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    val context = LocalContext.current
    val isGuardian = PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN

    UserAnalysisResultContent(
        onBack = onBack,
        viewModel = viewModel,
        isGuardian = isGuardian
    )
}

@Composable
private fun UserAnalysisResultContent(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel,
    isGuardian: Boolean = false
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
            AnalysisLoadingScreen(isLoading = viewModel.isLoading, onFinished = {}, isGuardian = isGuardian)
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
                targetValue = if (isToday) (if (isGuardian) AppColor.guardianDark else AppColor.accentDark) else AppColor.textSecondary,
                animationSpec = animSpec,
                label = "top_color"
            )

            val botColor by animateColorAsState(
                targetValue = if (isToday) (if (isGuardian) AppColor.guardianPrimary else AppColor.greenPrimary) else AppColor.textTertiary,
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
                                    color = if (isToday) (if (isGuardian) AppColor.guardianDark else AppColor.accentDark) else AppColor.textSecondary,
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
                    fontWeight = if (isDragging) FontWeight.ExtraBold else FontWeight.Normal,
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
                            isGuardian = isGuardian,
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
    isGuardian: Boolean = false,
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

    // Canvas는 @Composable 컨텍스트가 아니므로 색상 미리 캡처
    val colorPrimary       = if (isGuardian) AppColor.guardianPrimary else AppColor.greenPrimary
    val colorDark          = if (isGuardian) AppColor.guardianDark    else AppColor.accentDark
    val colorSecondary     = if (isGuardian) AppColor.guardianPrimary else AppColor.greenSecondary
    val colorTextTertiary  = AppColor.textTertiary
    // 기존 변수명 유지 (Canvas 블록에서 참조)
    val colorGreenPrimary   = colorPrimary
    val colorAccentDark     = colorDark
    val colorGreenSecondary = colorSecondary

    fun nearestIdx(offsetX: Float): Int {
        if (computedXs.isEmpty()) return -1

        return computedXs.indices.minByOrNull {
            abs(computedXs[it] - offsetX)
        }?.takeIf {
            hasData[it]
        } ?: -1
    }

    val density = LocalDensity.current
    // 포인트 X 계산에 쓰이는 좌우 여백 — Canvas 와 요일 Row 에서 동일하게 사용
    val hPadDp = 8.dp
    val hPadPx = with(density) { hPadDp.toPx() }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = hPadDp, vertical = 8.dp)
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
            val pad = hPadPx

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
                        colorGreenPrimary.copy(alpha = 0.35f),
                        colorGreenPrimary.copy(alpha = 0f)
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
                    colors = listOf(colorGreenPrimary, colorAccentDark),
                    startX = 0f,
                    endX = w
                ),
                style = Stroke(width = 8f)
            )

            for (i in points.indices) {
                val fraction = if (i == highlightIndex) (1f - abs(i - animatedHighlight)).coerceIn(0f, 1f) else 0f

                val dotColor = if (hasData[i]) {
                    if (fraction > 0.5f) colorGreenSecondary else colorAccentDark
                } else {
                    colorTextTertiary
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

        // Canvas 포인트 절대 X = 2*hPadPx + (fullWidth - 4*hPadPx) * i/(n-1)
        // BoxWithConstraints 에 padding 없이 fullWidth 로 동일 공식 적용
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fullW = with(density) { maxWidth.toPx() }
            val n = points.size
            points.forEachIndexed { i, point ->
                val xPx = 2 * hPadPx + (fullW - 4 * hPadPx) * (if (n > 1) i.toFloat() / (n - 1) else 0.5f) - with(density) { 5.dp.toPx() }
                val xDp = with(density) { xPx.toDp() }
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = xDp)
                        .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
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