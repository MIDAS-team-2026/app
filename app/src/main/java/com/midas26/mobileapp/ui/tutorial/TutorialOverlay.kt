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

    // 단계가 바뀌면 안내 카드 높이를 다시 측정한다.
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
    // 홈 튜토리얼의 위치 정보(5/23) 안내만 세로 여백을 줄인다.
    val compactLocationGuide = currentStep == 5 && totalSteps == 23 &&
            title == "위치 정보"
    val highlightBorderColor = tutorialColor

    // 전달받은 루트 좌표를 오버레이의 로컬 좌표로 변환한다.
    val localTargetBounds = targetBounds?.let { bounds ->
        val overlayRoot = overlayBoundsInRoot

        if (overlayRoot == null) {
            null // 오버레이 위치가 확정되기 전에는 잘못된 강조 영역을 표시하지 않는다.
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
        // 전체 안내 11단계는 점수 카드와 가깝게, 분석 다시 보기 2단계는
        // 그래프 카드 아래에 작은 간격을 두고 안내 창을 배치한다.
        val cardGapDp = when {
            currentStep == 11 && totalSteps == 23 && title == "세부 분석 결과" -> 8.dp
            currentStep == 2 && totalSteps == 3 && title == "이번 주 점수 추이" -> 12.dp
            else -> 24.dp
        }
        val cardMarginPx = with(density) { cardGapDp.toPx() }
        val fallbackCardHeightPx = with(density) { 230.dp.toPx() }

        val cardHeightPx = if (measuredCardHeightPx > 0f) {
            measuredCardHeightPx
        } else {
            fallbackCardHeightPx
        }

        val normalCardTopPx = when {
            expandedTarget == null ->
                ((screenHeightPx - cardHeightPx) / 2f)
                    .coerceAtLeast(cardMarginPx)

            expandedTarget.bottom + cardMarginPx + cardHeightPx <= screenHeightPx ->
                expandedTarget.bottom + cardMarginPx

            expandedTarget.top - cardMarginPx - cardHeightPx >= 0f ->
                expandedTarget.top - cardMarginPx - cardHeightPx

            // 대상이 화면 대부분을 차지하면 여유 공간이 더 큰 쪽에 안내 카드를 둔다.
            else -> {
                val spaceAbove = expandedTarget.top
                val spaceBelow = screenHeightPx - expandedTarget.bottom
                if (spaceAbove >= spaceBelow) {
                    cardMarginPx
                } else {
                    (screenHeightPx - cardHeightPx - cardMarginPx)
                        .coerceAtLeast(cardMarginPx)
                }
            }
        }

        val bottomGuidePadding = if (isBottomTabGuide && expandedTarget != null) {
            with(density) {
                (screenHeightPx - expandedTarget.top + cardMarginPx)
                    .coerceIn(0f, screenHeightPx)
                    .toDp()
            }
        } else {
            0.dp
        }

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
                // 테두리 선의 절반이 화면 밖으로 잘리지 않도록 안쪽으로 제한한다.
                val borderInset = with(density) { 3.dp.toPx() }
                val safeTarget = Rect(
                    left = target.left.coerceIn(borderInset, size.width - borderInset),
                    top = target.top.coerceIn(borderInset, size.height - borderInset),
                    right = target.right.coerceIn(borderInset, size.width - borderInset),
                    bottom = target.bottom.coerceIn(borderInset, size.height - borderInset)
                )

                // 화면 바깥의 영역은 캔버스 크기로 잘라낸다.
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

        val cardModifier = if (
            isBottomTabGuide &&
            expandedTarget != null
        ) {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = bottomGuidePadding
                )
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        x = 0,
                        y = normalCardTopPx.roundToInt()
                    )
                }
                .padding(horizontal = 20.dp)
        }

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
