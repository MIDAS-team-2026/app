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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.AppColor
import kotlinx.coroutines.delay

@Composable
fun AnalysisLoadingScreen(
    isLoading: Boolean,
    onFinished: () -> Unit
) {
    var step1 by remember { mutableStateOf(false) } // 서버 연결 완료
    var step2 by remember { mutableStateOf(false) } // 분석 데이터 수신 완료
    var step3 by remember { mutableStateOf(false) } // 결과 화면 준비 완료

    LaunchedEffect(Unit) {
        delay(400)
        step1 = true
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            step2 = true
            delay(400)
            step3 = true
        }
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
            text = "분석 결과를\n불러오는 중이에요",
            fontSize = 28.sp,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "오늘의 인지 건강 점수를\n가져오고 있어요",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))

        ProgressItem(label = "서버 연결 완료",       done = step1)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressItem(label = "분석 데이터 수신 완료", done = step2)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressItem(label = "결과 화면 준비 완료",   done = step3)
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
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = AppColor.accent,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
private fun ProgressItem(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (done) Green400 else Gray200),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) AppColor.textPrimary else AppColor.textTertiary,
            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
