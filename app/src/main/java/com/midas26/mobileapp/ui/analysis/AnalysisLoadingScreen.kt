package com.midas26.mobileapp.ui.analysis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.BrandWhite
import kotlinx.coroutines.delay

@Composable
fun AnalysisLoadingScreen(
    onFinished: () -> Unit
) {
    // 더미 진행 — 4초 후 결과로 이동
    LaunchedEffect(Unit) {
        delay(4000)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SpinningRing()
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "결과를 분석하고\n있어요",
            fontSize = 28.sp,
            color = Gray800,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "발화 속도, 어휘 다양성, 기억\n일치도를 종합 점검 중입니다",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray400,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))

        // 진행 항목
        ProgressItem(label = "음성 인식 완료",   done = true)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressItem(label = "어휘 분석 완료",   done = true)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressItem(label = "인지 점수 계산 중…", done = false)
    }
}

@Composable
private fun SpinningRing() {
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "rot"
    )
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // 회전하는 그라디언트 링
        Box(
            modifier = Modifier
                .size(140.dp)
                .rotate(rotation)
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        listOf(
                            Green400.copy(alpha = 0f),
                            Green400.copy(alpha = 0.2f),
                            Green400,
                            Green400.copy(alpha = 0.2f),
                            Green400.copy(alpha = 0f)
                        )
                    )
                )
        )
        // 가운데 흰 원
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(BrandWhite),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "📊", fontSize = 56.sp)
        }
    }
}

@Composable
private fun ProgressItem(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (done) Green400 else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Text(text = "✓", color = BrandWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Gray200)
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) Gray800 else Gray400,
            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
