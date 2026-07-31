package com.midas26.mobileapp.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/**
 * 실제 화면 위에 표시되는 게임형 튜토리얼 안내 UI입니다.
 *
 * targetBounds 영역을 투명하게 뚫어 실제 UI를 강조하고,
 * 해당 영역을 누르면 onTargetClick을 실행합니다.
 */
@Composable
fun TutorialOverlay(
    targetBounds: Rect?,
    message: String,
    currentStep: Int,
    totalSteps: Int,
    onNext: (() -> Unit)? = null,
    onTargetClick: (() -> Unit)? = null,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current
    val spotlightPaddingPx = with(density) { 8.dp.toPx() }
    val cornerRadiusPx = with(density) { 22.dp.toPx() }

    val expandedTarget = targetBounds?.let {
        Rect(
            left = it.left - spotlightPaddingPx,
            top = it.top - spotlightPaddingPx,
            right = it.right + spotlightPaddingPx,
            bottom = it.bottom + spotlightPaddingPx
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val cardMarginPx = with(density) { 20.dp.toPx() }
        val estimatedCardHeightPx = with(density) { 180.dp.toPx() }

        val cardTopPx = when {
            expandedTarget == null ->
                (screenHeightPx - estimatedCardHeightPx) / 2f

            expandedTarget.bottom + cardMarginPx + estimatedCardHeightPx <= screenHeightPx ->
                expandedTarget.bottom + cardMarginPx

            else ->
                (expandedTarget.top - cardMarginPx - estimatedCardHeightPx)
                    .coerceAtLeast(cardMarginPx)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .pointerInput(expandedTarget, onTargetClick, onNext) {
                    detectTapGestures { tapOffset ->
                        val target = expandedTarget

                        when {
                            target != null &&
                                    target.contains(tapOffset) &&
                                    onTargetClick != null -> {
                                onTargetClick()
                            }

                            target == null && onNext != null -> {
                                onNext()
                            }
                        }
                    }
                }
        ) {
            drawRect(
                color = Color.Black.copy(alpha = 0.72f)
            )

            expandedTarget?.let { target ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(target.left, target.top),
                    size = target.size,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    blendMode = BlendMode.Clear
                )

                drawRoundRect(
                    color = BrandWhite,
                    topLeft = Offset(target.left, target.top),
                    size = target.size,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = with(density) { 3.dp.toPx() }
                    )
                )
            }
        }

        TutorialMessageCard(
            message = message,
            currentStep = currentStep,
            totalSteps = totalSteps,
            showNextButton = onNext != null,
            onNext = onNext,
            onSkip = onSkip,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset {
                    IntOffset(
                        x = 0,
                        y = cardTopPx.roundToInt()
                    )
                }
                .padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun TutorialMessageCard(
    message: String,
    currentStep: Int,
    totalSteps: Int,
    showNextButton: Boolean,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
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

            Text(
                text = message,
                color = AppColor.textPrimary,
                fontSize = 21.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (showNextButton && onNext != null) {
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
            } else {
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