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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.Green900

@Composable
fun AnalysisResultScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    var showShareSheet by remember { mutableStateOf(false) }
    if (showShareSheet) {
        ShareToGuardianSheet(
            onDismiss = { showShareSheet = false },
            onShare = { showShareSheet = false /* TODO: 백엔드 공유 API 호출 */ },
            viewModel = viewModel
        )
    }
    val onShare: () -> Unit = { showShareSheet = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        // ── 상단 녹색 헤더 ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Green500, Green400),
                        start = Offset(Float.POSITIVE_INFINITY, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                    IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "보호자에게 공유",
                            tint = BrandWhite,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = viewModel.todayDateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.92f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = viewModel.todayScore.toString(),
                            fontSize = 56.sp,
                            color = BrandWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "점",
                            fontSize = 18.sp,
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
                                text = viewModel.scoreDeltaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColor.accentDark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = viewModel.scoreDeltaSubtext,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandWhite.copy(alpha = 0.92f)
                    )
                }
            }
        }

        // ── 본문 ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            // 주간 그래프 (배경에 바로)
            WeeklyLineChart(
                points = viewModel.graphPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

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
    }
}

// ── 주간 그래프 카드 ────────────────────────────────────────────────────────

@Composable
private fun WeeklyChartCard(points: List<DailyScore>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = 2.dp
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
private fun WeeklyLineChart(
    points: List<DailyScore>,
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

            val minScore = points.minOf { it.score }.toFloat() - 4f
            val maxScore = points.maxOf { it.score }.toFloat() + 4f
            val span = (maxScore - minScore).coerceAtLeast(1f)

            val xs = points.indices.map { i ->
                pad + (w - 2 * pad) * i / (points.size - 1).coerceAtLeast(1)
            }
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
            drawPath(path = linePath, color = Green600, style = Stroke(width = 4f))

            // 데이터 포인트
            for (i in points.indices) {
                val isLast = i == points.lastIndex
                val r = if (isLast) 11f else 5f
                drawCircle(color = BrandWhite, radius = r + 3f, center = Offset(xs[i], ys[i]))
                drawCircle(
                    color = if (isLast) Green900 else Green600,
                    radius = r,
                    center = Offset(xs[i], ys[i])
                )
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
    val accent = when (item.trend) {
        AnalysisItem.Trend.Up     -> AppColor.accentDark
        AnalysisItem.Trend.Down   -> AppColor.accent
        AnalysisItem.Trend.Steady -> AppColor.textTertiary
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                fontSize = 28.sp,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.trendText,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
