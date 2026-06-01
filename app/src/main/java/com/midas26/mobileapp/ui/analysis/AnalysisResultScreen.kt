package com.midas26.mobileapp.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray600
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.Green600
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale

@Composable
fun AnalysisResultScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    val fontScale = LocalFontSizeScale.current.scale

    var minTimeElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2000)
        minTimeElapsed = true
    }

    val showLoading = viewModel.isLoading || !minTimeElapsed

    Crossfade(
        targetState = showLoading,
        animationSpec = tween(durationMillis = 500),
        label = "analysis_crossfade"
    ) { loading ->
        if (loading) {
            AnalysisLoadingScreen(isLoading = viewModel.isLoading, onFinished = {})
            return@Crossfade
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        // ── 상단 헤더 (오늘=초록, 다른 날=회색, 애니메이션) ────────────
        val isToday = viewModel.isViewingToday
        val animSpec = tween<androidx.compose.ui.graphics.Color>(durationMillis = 400)
        val topColor by animateColorAsState(if (isToday) Green600 else Gray600, animSpec)
        val botColor by animateColorAsState(if (isToday) Green400 else Gray400, animSpec)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(brush = Brush.verticalGradient(colors = listOf(topColor, botColor)))
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
                // 앱바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (!isToday) viewModel.clearSelectedDay() else onBack() },
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
                        color = BrandWhite.copy(alpha = 0.92f)
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
                                color = if (isToday) AppColor.accentDark else Gray600,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 본문 ─────────────────────────────────────────────────────────
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            // 주간 그래프 카드
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = BrandWhite,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, AppColor.divider)
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
                        text = "최근 7일 · 포인트를 눌러 상세 확인",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.textTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WeeklyLineChart(
                        points = viewModel.graphPoints,
                        onPointTapped = { date -> viewModel.onGraphPointTapped(date) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 항목 카드 (2×2)
            val items = viewModel.todayItems
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ItemCard(item = items[0], modifier = Modifier.weight(1f))
                ItemCard(item = items[1], modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ItemCard(item = items[2], modifier = Modifier.weight(1f))
                ItemCard(item = items[3], modifier = Modifier.weight(1f))
            }
        }
        VerticalScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        } // Box
    }
    } // Crossfade
}

// ── 주간 그래프 카드 ────────────────────────────────────────────────────────

@Composable
private fun WeeklyChartCard(points: List<DailyScore>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = "이번 주 추이",
                style = MaterialTheme.typography.titleSmall,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            WeeklyLineChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}

@Composable
private fun DayDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColor.textTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = AppColor.textPrimary)
    }
}

@Composable
private fun WeeklyLineChart(
    points: List<DailyScore>,
    onPointTapped: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    // 탭 감지용 xs 공유
    var computedXs = remember { listOf<Float>() }
    var computedPad = remember { 16f }
    var computedW = remember { 0f }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (computedXs.isEmpty()) return@detectTapGestures
                        val tapRadius = 40f
                        val idx = computedXs.indexOfFirst { kotlin.math.abs(it - offset.x) < tapRadius }
                        if (idx >= 0) onPointTapped(points[idx].date)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pad = 16f
            computedW = w
            computedPad = pad

            val minScore = points.minOf { it.score }.toFloat() - 4f
            val maxScore = points.maxOf { it.score }.toFloat() + 4f
            val span = (maxScore - minScore).coerceAtLeast(1f)

            val xs = points.indices.map { i ->
                pad + (w - 2 * pad) * i / (points.size - 1).coerceAtLeast(1)
            }
            computedXs = xs
            val ys = points.map { p ->
                h - pad - (h - 2 * pad) * (p.score - minScore) / span
            }

            // 영역 채움
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
                    colors = listOf(Green400.copy(alpha = 0.35f), Green400.copy(alpha = 0f))
                )
            )

            // 라인
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
                    colors = listOf(Green400, Green600),
                    startX = 0f,
                    endX = w
                ),
                style = Stroke(width = 8f)
            )

            // 데이터 포인트
            for (i in points.indices) {
                val isLast = i == points.lastIndex
                if (isLast) {
                    // 바깥 흰 원 (테두리 역할)
                    drawCircle(color = BrandWhite, radius = 22f, center = Offset(xs[i], ys[i]))
                    // 초록 채움 원
                    drawCircle(color = Green500, radius = 15f, center = Offset(xs[i], ys[i]))
                } else {
                    drawCircle(color = BrandWhite, radius = 12f, center = Offset(xs[i], ys[i]))
                    drawCircle(color = Green600, radius = 8f, center = Offset(xs[i], ys[i]))
                }
            }
        }

        // X축 라벨
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { p ->
                Text(
                    text = p.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }
        }
    }
}

// ── 항목 카드 ───────────────────────────────────────────────────────────────

@Composable
private fun ItemCard(item: AnalysisItem, modifier: Modifier = Modifier) {
    val fontScale = LocalFontSizeScale.current.scale
    val accent = when (item.trend) {
        AnalysisItem.Trend.Up     -> AppColor.accentDark
        AnalysisItem.Trend.Down   -> AppColor.accent
        AnalysisItem.Trend.Steady -> AppColor.textTertiary
    }
    Surface(
        modifier = modifier.height((126 * fontScale).dp),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxSize()) {
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
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = item.valueText,
                fontSize = (28 * fontScale).sp,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.trendText,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
