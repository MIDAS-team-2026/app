package com.midas26.mobileapp.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import kotlin.math.roundToInt

@Composable
fun TutorialOverlay(
    targetBounds: Rect?,
    message: String,
    currentStep: Int,
    totalSteps: Int,
    onNext: (() -> Unit)? = null,
    onTargetClick: (() -> Unit)? = null,
    onSkip: () -> Unit,
    title: String? = null,
    isBottomTabGuide: Boolean = false,
    tutorialColor: Color = AppColor.greenPrimary
) {
    val density = LocalDensity.current

    var overlayBoundsInRoot by remember {
        mutableStateOf<Rect?>(null)
    }

    var measuredCardHeightPx by remember {
        mutableFloatStateOf(0f)
    }

    // 단계 변경 시 안내 카드 높이를 다시 측정합니다.
    LaunchedEffect(currentStep, title, message) {
        measuredCardHeightPx = 0f
    }

    val spotlightPaddingPx = with(density) {
        when {
            isBottomTabGuide -> 3.dp.toPx()
            title == "전화 연결 화면" -> 0.dp.toPx()
            else -> 8.dp.toPx()
        }
    }
    val cornerRadiusPx = with(density) {
        if (isBottomTabGuide) 16.dp.toPx() else 22.dp.toPx()
    }
    // 보호자 튜토리얼의 위치 정보 안내만 카드 여백을 줄입니다.
    val compactLocationGuide = currentStep == 5 && totalSteps == 23 &&
            title == "위치 정보"
    val highlightBorderColor = tutorialColor

    // 강조 영역 좌표를 오버레이 기준으로 변환합니다.
    val localTargetBounds = targetBounds?.let { bounds ->
        val overlayRoot = overlayBoundsInRoot

        if (overlayRoot == null) {
            null
        } else {
            Rect(
                left = bounds.left - overlayRoot.left,
                top = bounds.top - overlayRoot.top,
                right = bounds.right - overlayRoot.left,
                bottom = bounds.bottom - overlayRoot.top
            )
        }
    }

    val expandedTarget = localTargetBounds?.let { bounds ->
        Rect(
            left = bounds.left - spotlightPaddingPx,
            top = bounds.top - spotlightPaddingPx,
            right = bounds.right + spotlightPaddingPx,
            bottom = bounds.bottom + spotlightPaddingPx
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayBoundsInRoot = coordinates.boundsInRoot()
            }
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        // 하단 탭과 분석 안내 카드의 간격을 설정합니다.
        val cardGapDp = when {
            isBottomTabGuide && totalSteps == 19 -> 16.dp
            isBottomTabGuide -> 12.dp
            currentStep == 11 && totalSteps == 23 && title == "세부 분석 결과" -> 8.dp
            currentStep == 2 && totalSteps == 3 && title == "이번 주 점수 추이" -> 12.dp
            else -> 12.dp
        }
        val cardGapPx = with(density) { cardGapDp.toPx() }
        val screenEdgePx = with(density) { 12.dp.toPx() }
        val fallbackCardHeightPx = with(density) { 230.dp.toPx() }
        val cardHeightPx = measuredCardHeightPx.takeIf { it > 0f } ?: fallbackCardHeightPx
        val maxCardTopPx = (screenHeightPx - cardHeightPx - screenEdgePx).coerceAtLeast(0f)

        // 하단 탭 안내는 탭 위에, 일반 안내는 사용 가능한 공간에 배치합니다.
        val desiredCardTopPx = when {
            expandedTarget == null -> (screenHeightPx - cardHeightPx) / 2f
            isBottomTabGuide -> expandedTarget.top - cardGapPx - cardHeightPx
            expandedTarget.bottom + cardGapPx + cardHeightPx <= screenHeightPx - screenEdgePx ->
                expandedTarget.bottom + cardGapPx
            expandedTarget.top - cardGapPx - cardHeightPx >= screenEdgePx ->
                expandedTarget.top - cardGapPx - cardHeightPx
            else -> {
                val spaceAbove = expandedTarget.top
                val spaceBelow = screenHeightPx - expandedTarget.bottom
                if (spaceAbove >= spaceBelow) {
                    expandedTarget.top - cardGapPx - cardHeightPx
                } else {
                    expandedTarget.bottom + cardGapPx
                }
            }
        }
        val cardTopPx = desiredCardTopPx.coerceIn(0f, maxCardTopPx)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .pointerInput(
                    expandedTarget,
                    onTargetClick,
                    onNext,
                    isBottomTabGuide
                ) {
                    detectTapGestures { tapOffset ->
                        val target = expandedTarget

                        when {
                            target != null &&
                                    target.contains(tapOffset) &&
                                    onTargetClick != null -> onTargetClick()

                            target == null &&
                                    !isBottomTabGuide &&
                                    onNext != null -> onNext()
                        }
                    }
                }
        ) {
            drawRect(
                color = Color.Black.copy(
                    alpha = if (isBottomTabGuide) 0.58f else 0.72f
                )
            )

            expandedTarget?.let { target ->
                // 강조 테두리가 화면 밖으로 잘리지 않도록 제한합니다.
                val borderInset = with(density) { 3.dp.toPx() }
                val maxX = (size.width - borderInset).coerceAtLeast(borderInset)
                val maxY = (size.height - borderInset).coerceAtLeast(borderInset)
                val safeTarget = Rect(
                    left = target.left.coerceIn(borderInset, maxX),
                    top = target.top.coerceIn(borderInset, maxY),
                    right = target.right.coerceIn(borderInset, maxX),
                    bottom = target.bottom.coerceIn(borderInset, maxY)
                )

                if (safeTarget.width > 0f && safeTarget.height > 0f) {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(safeTarget.left, safeTarget.top),
                        size = safeTarget.size,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        blendMode = BlendMode.Clear
                    )

                    drawRoundRect(
                        color = highlightBorderColor,
                        topLeft = Offset(safeTarget.left, safeTarget.top),
                        size = safeTarget.size,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(
                            width = with(density) {
                                if (isBottomTabGuide) 4.dp.toPx() else 3.dp.toPx()
                            }
                        )
                    )
                }
            }
        }

        val cardModifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(x = 0, y = cardTopPx.roundToInt()) }
            .padding(horizontal = 20.dp)

        TutorialMessageCard(
            title = title,
            message = message,
            currentStep = currentStep,
            totalSteps = totalSteps,
            showNextButton = onNext != null,
            showBottomTabGuide = isBottomTabGuide,
            onNext = onNext,
            onSkip = onSkip,
            tutorialColor = tutorialColor,
            compact = compactLocationGuide,
            onCardHeightChanged = { heightPx ->
                if (heightPx > 0f && measuredCardHeightPx != heightPx) {
                    measuredCardHeightPx = heightPx
                }
            },
            modifier = cardModifier
        )
    }
}

@Composable
private fun TutorialMessageCard(
    title: String?,
    message: String,
    currentStep: Int,
    totalSteps: Int,
    showNextButton: Boolean,
    showBottomTabGuide: Boolean,
    onNext: (() -> Unit)?,
    onSkip: () -> Unit,
    tutorialColor: Color,
    compact: Boolean,
    onCardHeightChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .onGloballyPositioned { coordinates ->
                onCardHeightChanged(
                    coordinates.size.height.toFloat()
                )
            },
        shape = RoundedCornerShape(24.dp),
        color = BrandWhite,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 22.dp,
                vertical = if (compact) 12.dp else 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentStep / $totalSteps",
                    color = tutorialColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "건너뛰기",
                    color = AppColor.textTertiary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onSkip)
                        .padding(6.dp)
                )
            }

            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = AppColor.textPrimary,
                    fontSize = if (compact) 21.sp else 22.sp,
                    lineHeight = if (compact) 27.sp else 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = message,
                color = AppColor.textPrimary,
                fontSize = if (title.isNullOrBlank()) 21.sp else 18.sp,
                lineHeight = if (compact) 25.sp else if (title.isNullOrBlank()) 30.sp else 27.sp,
                fontWeight = if (title.isNullOrBlank()) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (showBottomTabGuide) {
                BottomTabGuideMessage(tutorialColor)
            }

            when {
                showNextButton && onNext != null -> {
                    if (tutorialColor == AppColor.greenPrimary) {
                        Text(
                            text = "다음",
                            color = BrandWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = tutorialColor,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(onClick = onNext)
                                .padding(vertical = 14.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "다음",
                                color = tutorialColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable(onClick = onNext)
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        text = "강조된 부분을 눌러보세요",
                        color = AppColor.textTertiary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabGuideMessage(
    tutorialColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "강조된 하단 탭을 눌러보세요",
            color = tutorialColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "▼",
            color = tutorialColor,
            fontSize = 24.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
