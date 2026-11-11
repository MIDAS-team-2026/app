package com.midas26.mobileapp.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.location.LocationForegroundService
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay

// 피그마 로딩 화면 배경색 (#FBFFF8)
private val SplashBg  = Color(0xFFFBFFF8)
// 로딩 점 색상 — 로고 말풍선의 세이지 그린 계열
private val DotColor  = Color(0xFF6BAB8F)

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    isDataReady: Boolean = true,
    hasNetworkError: Boolean = false,
) {
    val context = LocalContext.current
    val isDataReadyState = rememberUpdatedState(isDataReady)
    val hasNetworkErrorState = rememberUpdatedState(hasNetworkError)

    LaunchedEffect(Unit) {
        val prefs = PrefsManager.from(context)
        if (!prefs.isLoggedIn()) {
            delay(1800)
            if (prefs.hasSeenOnboarding()) onNavigateToLogin() else onNavigateToOnboarding()
            return@LaunchedEffect
        }

        // 위치 공유 토글이 켜져 있으면 서비스 재시작
        if (prefs.getLocationSharingEnabled()) {
            val intent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // 로그인 상태: 최소 1800ms + 데이터 로드 완료(또는 에러) 대기
        delay(1800)
        snapshotFlow { isDataReadyState.value || hasNetworkErrorState.value }
            .filter { it }
            .first()
        if (hasNetworkErrorState.value) onNavigateToLogin() else onNavigateToHome()
    }

    // 점 3개 순차 페이드 애니메이션 (1.2s 루프)
    val infinite = rememberInfiniteTransition(label = "dots")
    val dot1 by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1200
            0.9f  at 0    using LinearEasing
            0.2f  at 300  using LinearEasing
            0.9f  at 600  using LinearEasing
            0.9f  at 1200 using LinearEasing
        }, RepeatMode.Restart), label = "d1"
    )
    val dot2 by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1200
            0.5f  at 0    using LinearEasing
            0.9f  at 300  using LinearEasing
            0.2f  at 600  using LinearEasing
            0.5f  at 1200 using LinearEasing
        }, RepeatMode.Restart), label = "d2"
    )
    val dot3 by infinite.animateFloat(
        initialValue = 0.2f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1200
            0.2f  at 0    using LinearEasing
            0.5f  at 300  using LinearEasing
            0.9f  at 600  using LinearEasing
            0.2f  at 1200 using LinearEasing
        }, RepeatMode.Restart), label = "d3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = "똑똑 로고",
            modifier = Modifier
                .size(width = 321.dp, height = 296.dp)
        )

        // 하단 로딩 점 3개
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(dot1, dot2, dot3).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DotColor.copy(alpha = alpha))
                )
            }
        }
    }
}
