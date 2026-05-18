package com.midas26.mobileapp.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalDensity
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Green900
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.AppColor

@Composable
fun AnalysisGraphScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    var showShareSheet by remember { mutableStateOf(false) }
    if (showShareSheet) {
        ShareToGuardianSheet(
            onDismiss = { showShareSheet = false },
            onShare = { showShareSheet = false },
            viewModel = viewModel
        )
    }
    val onShare: () -> Unit = { showShareSheet = true }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        // 앱바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = AppColor.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "추이 그래프",
                style = MaterialTheme.typography.titleLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "보호자에게 공유",
                    tint = AppColor.textPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // 일/주/월 탭
            RangeTabs(
                selected = viewModel.graphRange,
                onSelect = viewModel::selectRange
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 평균 점수
            Text(
                text = when (viewModel.graphRange) {
                    TrendRange.DAY   -> "오늘 인지 점수"
                    TrendRange.WEEK  -> "최근 7일 인지 점수"
                    TrendRange.MONTH -> "최근 30일 인지 점수"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "%.1f".format(viewModel.graphAverage),
                    fontSize = 32.sp,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.size(8.dp))
                Surface(shape = CircleShape, color = Green50) {
                    Text(
                        text = viewModel.graphAverageDeltaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.accentDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 라인 차트
            LineChart(
                points = viewModel.graphPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 항목별 추이
            Text(
                text = "항목별 추이",
                style = MaterialTheme.typography.titleMedium,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            viewModel.trendItems.forEach { item ->
                TrendRow(item = item)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RangeTabs(
    selected: TrendRange,
    onSelect: (TrendRange) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Gray100
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            TrendRange.values().forEach { range ->
                val isSelected = range == selected
                val label = when (range) {
                    TrendRange.DAY   -> "일"
                    TrendRange.WEEK  -> "주"
                    TrendRange.MONTH -> "월"
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { onSelect(range) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) BrandWhite else Color.Transparent,
                    shadowElevation = if (isSelected) 1.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) AppColor.textPrimary else AppColor.textTertiary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 라인 차트 (Compose Canvas 직접 그리기).
 * - 부드러운 곡선 (cubic Bezier)
 * - 영역 그라디언트 채움
 * - 데이터 포인트 마지막은 강조 (큰 원 + 라벨 툴팁)
 * - 하단 X축 라벨
 */
@Composable
private fun LineChart(
    points: List<DailyScore>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val density = LocalDensity.current

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

            val minScore = points.minOf { it.score }.toFloat() - 4f
            val maxScore = points.maxOf { it.score }.toFloat() + 4f
            val span = (maxScore - minScore).coerceAtLeast(1f)

            val xs = points.indices.map { i -> pad + (w - 2 * pad) * i / (points.size - 1).coerceAtLeast(1) }
            val ys = points.map { p -> h - pad - (h - 2 * pad) * (p.score - minScore) / span }

            // 가로 격자선 3개
            val gridColor = Gray100
            for (k in 1..3) {
                val gy = pad + (h - 2 * pad) * k / 4
                drawLine(
                    color = gridColor,
                    start = Offset(pad, gy),
                    end = Offset(w - pad, gy),
                    strokeWidth = 1.5f
                )
            }

            // 영역 채움 (그라디언트)
            val areaPath = Path().apply {
                moveTo(xs.first(), h - pad)
                lineTo(xs.first(), ys.first())
                for (i in 1 until xs.size) {
                    val cx1 = (xs[i - 1] + xs[i]) / 2f
                    val cy1 = ys[i - 1]
                    val cx2 = (xs[i - 1] + xs[i]) / 2f
                    val cy2 = ys[i]
                    cubicTo(cx1, cy1, cx2, cy2, xs[i], ys[i])
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
                    val cx1 = (xs[i - 1] + xs[i]) / 2f
                    val cy1 = ys[i - 1]
                    val cx2 = (xs[i - 1] + xs[i]) / 2f
                    val cy2 = ys[i]
                    cubicTo(cx1, cy1, cx2, cy2, xs[i], ys[i])
                }
            }
            drawPath(
                path = linePath,
                color = Green600,
                style = Stroke(width = 4f)
            )

            // 데이터 포인트
            for (i in points.indices) {
                val isLast = i == points.lastIndex
                val r = if (isLast) 8f else 5f
                drawCircle(color = BrandWhite, radius = r + 3f, center = Offset(xs[i], ys[i]))
                drawCircle(color = if (isLast) Green900 else Green600, radius = r, center = Offset(xs[i], ys[i]))
            }
        }

        // X축 라벨
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 길면 일부만 보여주기 (월 단위는 30개)
            val labels = if (points.size > 10) {
                val step = points.size / 6
                points.filterIndexed { idx, _ -> idx % step == 0 || idx == points.lastIndex }
            } else points
            labels.forEach { p ->
                Text(
                    text = p.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }
        }
    }
}

@Composable
private fun TrendRow(item: AnalysisItem) {
    val (bg, fg) = when (item.trend) {
        AnalysisItem.Trend.Up     -> Green50 to Green600
        AnalysisItem.Trend.Steady -> Green50 to Green400
        AnalysisItem.Trend.Down   -> Green50 to Green400
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.trendText,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
