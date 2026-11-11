package com.midas26.mobileapp.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 드래그 가능한 오버레이 스크롤바 Composable.
 * 스크롤 콘텐츠 Box의 자식으로, align(Alignment.TopEnd)에 배치하면
 * 헤더/네비에 잘리지 않고 올바른 위치에 표시됩니다.
 *
 * 사용 예시:
 * Box {
 *     Column(Modifier.verticalScroll(scrollState)) { ... }
 *     VerticalScrollbar(state = scrollState, modifier = Modifier.align(Alignment.TopEnd))
 * }
 */
@Composable
fun VerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
    trackColor: Color = Color(0xFFE5E7EA).copy(alpha = 0.9f),
    thumbColor: Color = Color(0xFF6B7280).copy(alpha = 0.7f),
    paddingEnd: Dp = 6.dp,
    paddingVertical: Dp = 8.dp
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(width + paddingEnd)
            .fillMaxHeight()
            .padding(end = paddingEnd, top = paddingVertical, bottom = paddingVertical)
            .pointerInput(state.maxValue) {
                // 트랙 탭 → 해당 위치로 점프
                detectTapGestures { offset ->
                    val maxScroll = state.maxValue.toFloat()
                    if (maxScroll > 0f) {
                        val ratio = offset.y / size.height
                        scope.launch { state.scrollTo((ratio * maxScroll).toInt().coerceIn(0, maxScroll.toInt())) }
                    }
                }
            }
            .pointerInput(state.maxValue) {
                // 드래그 → 비례해서 스크롤
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val maxScroll = state.maxValue.toFloat()
                    if (maxScroll > 0f) {
                        val dragRatio = dragAmount.y / size.height
                        val delta = (dragRatio * maxScroll).toInt()
                        scope.launch { state.scrollTo((state.value + delta).coerceIn(0, maxScroll.toInt())) }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val maxScroll = state.maxValue.toFloat()
                if (maxScroll > 0f) {
                    val w = size.width
                    val viewH = size.height
                    val contentH = viewH + maxScroll
                    val thumbH = (viewH / contentH * viewH).coerceAtLeast(48.dp.toPx())
                    val thumbY = (state.value / maxScroll) * (viewH - thumbH)
                    val radius = CornerRadius(w / 2f)

                    // 트랙
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(w, viewH),
                        cornerRadius = radius
                    )
                    // thumb
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(0f, thumbY),
                        size = Size(w, thumbH),
                        cornerRadius = radius
                    )
                }
            }
    ) {}
}
