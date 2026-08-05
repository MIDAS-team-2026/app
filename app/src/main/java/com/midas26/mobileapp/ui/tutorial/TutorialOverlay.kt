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
    isBottomTabGuide: Boolean = false
) {
    val density = LocalDensity.current
    val spotlightPaddingPx = with(density) {
        if (isBottomTabGuide) 3.dp.toPx() else 8.dp.toPx()
    }
    val cornerRadiusPx = with(density) {
        if (isBottomTabGuide) 16.dp.toPx() else 22.dp.toPx()
    }
    val highlightBorderColor = if (isBottomTabGuide) {
        AppColor.greenPrimary
    } else {
        BrandWhite
    }

    val expandedTarget = targetBounds?.let { bounds ->
        Rect(
            left = bounds.left - spotlightPaddingPx,
            top = bounds.top - spotlightPaddingPx,
            right = bounds.right + spotlightPaddingPx,
            bottom = bounds.bottom + spotlightPaddingPx
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val cardMarginPx = with(density) { 14.dp.toPx() }
        val estimatedCardHeightPx = with(density) { 190.dp.toPx() }

        val normalCardTopPx = when {
            expandedTarget == null ->
                (screenHeightPx - estimatedCardHeightPx) / 2f

            expandedTarget.bottom + cardMarginPx + estimatedCardHeightPx <= screenHeightPx ->
                expandedTarget.bottom + cardMarginPx

            else ->
                (expandedTarget.top - cardMarginPx - estimatedCardHeightPx)
                    .coerceAtLeast(cardMarginPx)
        }

        val bottomGuidePadding = if (
            isBottomTabGuide &&
            expandedTarget != null
        ) {
            with(density) {
                (screenHeightPx - expandedTarget.top + cardMarginPx).toDp()
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
                val safeTarget = Rect(
                    left = target.left.coerceAtLeast(0f),
                    top = target.top.coerceAtLeast(0f),
                    right = target.right.coerceAtMost(size.width),
                    bottom = target.bottom.coerceAtMost(size.height)
                )

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
            showNextButton = onNext != null && !isBottomTabGuide,
            showBottomTabGuide = isBottomTabGuide,
            onNext = onNext,
            onSkip = onSkip,
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = BrandWhite,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 22.dp,
                vertical = 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentStep / $totalSteps",
                    color = AppColor.greenPrimary,
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
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = message,
                color = AppColor.textPrimary,
                fontSize = if (title.isNullOrBlank()) 21.sp else 18.sp,
                lineHeight = if (title.isNullOrBlank()) 30.sp else 27.sp,
                fontWeight = if (title.isNullOrBlank()) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                showBottomTabGuide -> BottomTabGuideMessage()

                showNextButton && onNext != null -> {
                    Text(
                        text = "다음",
                        color = BrandWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = AppColor.greenPrimary,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(onClick = onNext)
                            .padding(vertical = 14.dp)
                    )
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
private fun BottomTabGuideMessage() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "강조된 하단 탭을 눌러보세요",
            color = AppColor.greenPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "▼",
            color = AppColor.greenPrimary,
            fontSize = 24.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}