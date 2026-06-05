package com.midas26.mobileapp.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppOutlineButton
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor

@Composable
fun LoginScreen(
    onNavigateToLoginForm: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onAccessibility: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 중앙 콘텐츠 ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = "앱 로고",
                modifier = Modifier.size(260.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            AppPrimaryButton(
                text = stringResource(R.string.btn_login),
                onClick = onNavigateToLoginForm
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppOutlineButton(
                text = stringResource(R.string.btn_signup),
                onClick = onNavigateToSignup
            )
        }

        // ── 접근성 버튼 (우측 하단) ──────────────────────────────────────
        Text(
            text = "접근성 설정",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color(0xFF1A73E8),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
                .clickable(onClick = onAccessibility)
        )
    }
}
