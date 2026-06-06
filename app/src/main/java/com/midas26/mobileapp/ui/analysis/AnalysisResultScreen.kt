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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.midas26.mobileapp.ui.theme.LocalHapticEnabled
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.midas26.mobileapp.ui.theme.GuardianAccent
import com.midas26.mobileapp.ui.theme.GuardianAccentDark
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import androidx.compose.ui.platform.LocalContext
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun AnalysisResultScreen(
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = viewModel()
) {
    val fontScale = LocalFontSizeScale.current.scale
    val context = LocalContext.current
    val isGuardian = PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN

    // 로딩 화면은 showLoadingScreen이 true일 때만 (최초 진입 또는 음성 대화 후)
    var minTimeElapsed by remember { mutableStateOf(!viewModel.showLoadingScreen) }
    LaunchedEffect(viewModel.showLoadingScreen) {
        if (viewModel.showLoadingScreen) {
            minTimeElapsed = false
            delay(2000)
            minTimeElapsed = true
        }
    }

    val showLoading = viewModel.showLoadingScreen && (viewModel.isLoading || !minTimeElapsed)

    // 로딩이 끝나면 다음 진입부터는 로딩 화면 생략
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

    Column(modifier = Modifier.fillMaxSize().background(BrandWhite)) {
        // ── 상단 헤더 (오늘=초록, 다른 날=회색, 애니메이션) ────────────
        val isToday = viewModel.isViewingToday && viewModel.hasTodayData
        val animSpec = tween<androidx.compose.ui.graphics.Color>(durationMillis = 400)
        val topColor by animateColorAsState(
            if (isGuardian) GuardianAccentDark
            else if (isToday) AppColor.accentDark else AppColor.textSecondary,
            animSpec
        )
        val botColor by animateColorAsState(
            if (isGuardian) GuardianAccent
            else if (isToday) AppColor.greenPrimary else AppColor.textTertiary,
            animSpec
        )
        // 헤더 전체 — 배경 그라디언트
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(brush = Brush.verticalGradient(colors = listOf(topColor, botColor)))
        ) {
            // 레이어 1: 헤더 콘텐츠
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
                    // 날짜 라벨 자리 확보용 투명 텍스트 (레이아웃 높이 유지)
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

            // 레이어 2: 헤더 스크림
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )

            // 레이어 3: 날짜 라벨 — 스크림 위에 강조 (Box 직접 자식으로 항상 최상위)
            // bottom = 28(하단패딩) + 16(Spacer) + 점수Row높이(약 56sp + badge)
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

        // ── 본문 ─────────────────────────────────────────────────────────

        // 그래프 카드 실제 높이 (항목 카드 영역 상단 오프셋 동기화용)
        var graphCardHeightPx by remember { mutableStateOf(0) }
        val density = LocalDensity.current
        val graphCardHeightDp = with(density) { graphCardHeightPx.toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            // ① 항목 카드 영역 — 그래프 카드 아래부터 화면 끝까지, 스크롤 없음
            val items = viewModel.todayItems
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp + graphCardHeightDp)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.weight(2f))  // 그래프 ↔ 상단 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ItemCard(item = items[0], modifier = Modifier.weight(1f))
                    ItemCard(item = items[1], modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.weight(1f))  // 상단 Row ↔ 하단 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ItemCard(item = items[2], modifier = Modifier.weight(1f))
                    ItemCard(item = items[3], modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.weight(2f))  // 하단 Row ↔ 네비게이션
            }

            // ② 전체 스크림 — 화면 전체를 덮음
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )

            // ③ 그래프 카드 — 스크림 위에 항상 밝게 떠 있음 (Box에서 마지막 = 최상위)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .onGloballyPositioned { coords -> graphCardHeightPx = coords.size.height },
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

        } // 본문 Box
    } // Column
    } // Crossfade
}

// ── 주간 그래프 카드 ────────────────────────────────────────────────────────


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

    val highlightIdx = highlightIndex

    // score == 0인 날 = 데이터 없음
    val hasData = points.map { it.score > 0 }

    // 그래프용 점수: 데이터 없는 날은 이전 값으로 채움 (forward-fill)
    val filledScores = run {
        val result = points.map { it.score }.toMutableList()
        for (i in result.indices) {
            if (result[i] == 0) {
                result[i] = if (i > 0) result[i - 1] else
                    result.firstOrNull { it > 0 } ?: 0
            }
        }
        result
    }

    // 강조 인덱스를 float으로 애니메이션 → 포인트 크기 서서히 변화 (-1이면 강조 없음)
    val animatedHighlight by animateFloatAsState(
        targetValue = if (highlightIdx >= 0) highlightIdx.toFloat() else -999f,
        animationSpec = tween(durationMillis = 300),
        label = "highlight"
    )

    // 탭/롱프레스+드래그 감지용 xs 공유
    var computedXs = remember { listOf<Float>() }
    // 드래그 중 마지막으로 강조했던 인덱스 (진동 중복 방지)
    var lastDragIdx = remember { -1 }

    fun nearestIdx(offsetX: Float): Int {
        if (computedXs.isEmpty()) return -1
        return computedXs.indices.minByOrNull { abs(computedXs[it] - offsetX) }
            ?.takeIf { hasData[it] } ?: -1
    }

    // Canvas DrawScope는 @Composable 컨텍스트가 아니므로 미리 캡처
    val colorGreenPrimary   = AppColor.greenPrimary
    val colorAccentDark     = AppColor.accentDark
    val colorGreenSecondary = AppColor.greenSecondary
    val colorTextTertiary   = AppColor.textTertiary

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

                        loop@ while (true) {
                            val ev = awaitPointerEvent()
                            val pos = ev.changes.firstOrNull()?.position ?: break
                            val elapsed = System.currentTimeMillis() - downTime

                            if (!longPressTriggered) {
                                if (elapsed >= longPressMs) {
                                    // 롱프레스 발동
                                    longPressTriggered = true
                                    isDragging = true
                                    onDragStart()
                                    val idx = nearestIdx(pos.x)
                                    if (idx >= 0) {
                                        lastDragIdx = idx
                                        endDate = points[idx].date
                                        onDragMove(endDate)
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            } else {
                                // 드래그 중 — 포인터 이동마다 날짜 업데이트
                                val idx = nearestIdx(pos.x)
                                if (idx >= 0 && idx != lastDragIdx) {
                                    lastDragIdx = idx
                                    endDate = points[idx].date
                                    onDragMove(endDate)
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }

                            ev.changes.forEach { it.consume() }

                            if (ev.changes.none { it.pressed }) {
                                // 손가락을 뗌
                                if (isDragging) {
                                    onDragEnd(endDate)
                                } else {
                                    // 롱프레스 전에 뗌 → 일반 탭
                                    val idx = nearestIdx(down.position.x)
                                    if (idx >= 0) {
                                        onPointTapped(points[idx].date)
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                break@loop
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
                    colors = listOf(colorGreenPrimary.copy(alpha = 0.35f), colorGreenPrimary.copy(alpha = 0f))
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
                    colors = listOf(colorGreenPrimary, colorAccentDark),
                    startX = 0f,
                    endX = w
                ),
                style = Stroke(width = 8f)
            )

            // 데이터 포인트: animatedHighlight 기준으로 크기 서서히 변화
            for (i in points.indices) {
                // 0~1 사이 강조 비율 (1 = 완전 강조, 0 = 일반)
                val fraction = (1f - abs(i - animatedHighlight)).coerceIn(0f, 1f)
                val dotColor = if (hasData[i]) {
                    if (fraction > 0.5f) colorGreenSecondary else colorAccentDark
                } else {
                    colorTextTertiary
                }
                val outerRadius = lerp(12f, 28f, fraction)
                val innerRadius = lerp(8f,  19f, fraction)
                drawCircle(color = BrandWhite, radius = outerRadius, center = Offset(xs[i], ys[i]))
                drawCircle(color = dotColor,   radius = innerRadius,  center = Offset(xs[i], ys[i]))
            }
        }

        // X축 라벨
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { i, p ->
                Text(
                    text = p.dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasData[i]) AppColor.textTertiary else AppColor.textTertiary
                )
            }
        }
    }
}

// ── 항목 카드 ───────────────────────────────────────────────────────────────

@Composable
private fun ItemCard(item: AnalysisItem, modifier: Modifier = Modifier) {
    val fontScale = LocalFontSizeScale.current.scale
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, AppColor.divider)
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
