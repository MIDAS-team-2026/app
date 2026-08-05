package com.midas26.mobileapp.ui.analysis

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite

@Composable
fun LinguisticMarkerDetailScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 20.dp, top = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = AppColor.textPrimary
                )
            }

            Text(
                text = "어휘 점수 상세",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        }

        Text(
            text = "최근 7일 · 점선은 평소 평균",
            style = MaterialTheme.typography.bodySmall,
            color = AppColor.textTertiary,
            modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)
        )

        val markers = viewModel.linguisticMarkers

        if (markers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "아직 분석된 대화가 없어요. 대화를 나누면 지표가 쌓여요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary
                )
            }
            return@Column
        }

        val scrollState = rememberScrollState()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                markers.forEach { marker ->
                    MarkerDetailCard(marker = marker)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            VerticalScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun MarkerDetailCard(marker: LinguisticMarkerUi) {
    val (badgeBg, badgeText, lineColor) = when (marker.status) {
        MarkerStatus.GOOD -> Triple(AppColor.greenSurface, AppColor.accentDark, AppColor.accentDark)
        MarkerStatus.WATCH -> Triple(AppColor.amberSurface, AppColor.amberDark, AppColor.amberDark)
        MarkerStatus.DANGER -> Triple(AppColor.errorSurface, AppColor.errorPrimary, AppColor.errorPrimary)
        MarkerStatus.INSUFFICIENT_DATA -> Triple(AppColor.surfaceElevated, AppColor.textTertiary, AppColor.textTertiary)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = marker.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Surface(shape = RoundedCornerShape(999.dp), color = badgeBg) {
                    Text(
                        text = marker.status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = badgeText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MarkerLineChart(
                points = marker.weeklyPoints,
                baselineMean = marker.baselineMean,
                lineColor = lineColor
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = marker.diffText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = badgeText
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = marker.diffDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textSecondary
                )
            }
        }
    }
}

private val AverageLineColor = Color(0xFF6B7280)

@Composable
private fun MarkerLineChart(
    points: List<LinguisticMarkerPoint>,
    baselineMean: Float?,
    lineColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        val values = points.mapNotNull { it.value }
        if (values.isEmpty()) return@Canvas

        val allValues = if (baselineMean != null) values + baselineMean else values
        val minValue = allValues.min()
        val maxValue = allValues.max()
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f

        val padding = 8.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        val stepX = if (points.size > 1) chartWidth / (points.size - 1) else 0f

        fun xFor(index: Int) = padding + stepX * index
        fun yFor(value: Float) = padding + chartHeight - ((value - minValue) / range) * chartHeight

        if (baselineMean != null) {
            val y = yFor(baselineMean)
            drawLine(
                color = AverageLineColor,
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }

        val path = Path()
        var started = false

        points.forEachIndexed { index, point ->
            val value = point.value ?: return@forEachIndexed
            val x = xFor(index)
            val y = yFor(value)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

        points.forEachIndexed { index, point ->
            val value = point.value ?: return@forEachIndexed
            val isLast = index == points.lastIndex
            drawCircle(
                color = lineColor,
                radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                center = Offset(xFor(index), yFor(value))
            )
        }
    }
}
