package com.midas26.mobileapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay

/**
 * Splash 화면. 1.8초 후 PrefsManager 상태에 따라 분기:
 *  - 토큰 있음   → Home
 *  - 온보딩 봤음 → Login
 *  - 그 외       → Onboarding
 */
@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(1800)
        val prefs = PrefsManager.from(context)
        when {
            prefs.isLoggedIn() -> onNavigateToHome()
            prefs.hasSeenOnboarding() -> onNavigateToLogin()
            else -> onNavigateToOnboarding()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Green400
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 로고 카드 (96dp → 120dp)
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = BrandWhite,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = Green400,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    color = BrandWhite,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.app_tagline),
                    color = Color(0xCCFFFFFF),
                    fontSize = 18.sp,
                    letterSpacing = 0.4.sp
                )
            }

            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
                    .size(36.dp),
                color = BrandWhite,
                strokeWidth = 4.dp
            )
        }
    }
}
