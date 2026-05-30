package com.midas26.mobileapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.midas26.mobileapp.ui.navigation.AppNavHost
import com.midas26.mobileapp.ui.navigation.Routes
import com.midas26.mobileapp.ui.onboarding.SplashScreen
import com.midas26.mobileapp.ui.theme.AppTheme
import com.midas26.mobileapp.ui.theme.FontSizeLevel
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.theme.LocalHapticEnabled
import com.midas26.mobileapp.ui.theme.LocalHighContrast
import com.midas26.mobileapp.ui.theme.LocalTapToReplay
import com.midas26.mobileapp.ui.theme.LocalTtsManager
import com.midas26.mobileapp.util.PrefsManager
import com.midas26.mobileapp.util.TtsManager

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsManager.from(this)
        ttsManager = TtsManager(this).apply { setSpeed(prefs.getTtsSpeed()) }
        enableEdgeToEdge()
        setContent {
            AppRoot(ttsManager = ttsManager)
        }
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }
}

@Composable
fun AppRoot(ttsManager: TtsManager? = null) {
    val context = LocalContext.current
    val prefs = PrefsManager.from(context)
    var fontSizeLevel by remember {
        mutableStateOf(FontSizeLevel.fromIndex(prefs.getAccessibilityFontSize()))
    }
    var highContrast by remember { mutableStateOf(prefs.getHighContrast()) }
    var hapticEnabled by remember { mutableStateOf(prefs.getHapticFeedback()) }
    var tapToReplay by remember { mutableStateOf(prefs.getTapToReplay()) }
    var voiceChatEnabled by remember { mutableStateOf(prefs.getVoiceChatEnabled()) }

    val navController = rememberNavController()
    var splashVisible by remember { mutableStateOf(true) }

    // 앱 시작 시 한 번만 계산 — NavHost가 처음부터 올바른 화면에서 시작
    val startDestination = remember {
        when {
            prefs.isLoggedIn() -> if (prefs.getUserRole() == PrefsManager.ROLE_GUARDIAN)
                Routes.GuardianHome else Routes.UserHome
            prefs.hasSeenOnboarding() -> Routes.Login
            else -> Routes.Onboarding
        }
    }

    CompositionLocalProvider(
        LocalFontSizeScale provides fontSizeLevel,
        LocalHighContrast provides highContrast,
        LocalHapticEnabled provides hapticEnabled,
        LocalTapToReplay provides tapToReplay,
        LocalTtsManager provides if (voiceChatEnabled) ttsManager else null
    ) {
        AppTheme {
            // 최상위 Box — Scaffold 패딩 적용 전, 진짜 전체 화면
            Box(modifier = Modifier.fillMaxSize()) {

                // ── 메인 앱 (Scaffold + NavHost) ─────────────────────────
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AppNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            onFontSizeChange = { level ->
                                fontSizeLevel = level
                                prefs.setAccessibilityFontSize(level.ordinal)
                            },
                            onHighContrastChange = { enabled ->
                                highContrast = enabled
                                prefs.setHighContrast(enabled)
                            },
                            onHapticChange = { enabled ->
                                hapticEnabled = enabled
                                prefs.setHapticFeedback(enabled)
                            },
                            onTapToReplayChange = { enabled ->
                                tapToReplay = enabled
                            },
                            onSpeedChange = { speed ->
                                ttsManager?.setSpeed(speed)
                            },
                            onVoiceChatEnabledChange = { enabled ->
                                voiceChatEnabled = enabled
                            },
                            onPreviewTts = {
                                ttsManager?.speak("안녕하세요. 이 속도로 음성이 재생됩니다.")
                            }
                        )
                    }
                }

                // ── Splash 오버레이 — Scaffold/BottomBar 포함 전체 화면을 덮음 ──
                // NavHost는 이미 startDestination에서 시작하므로 오버레이만 제거하면 됨
                AnimatedVisibility(
                    visible = splashVisible,
                    enter   = androidx.compose.animation.EnterTransition.None,
                    exit    = fadeOut(animationSpec = tween(durationMillis = 500))
                ) {
                    // 페이드아웃 애니메이션이 완전히 끝날 때까지 터치 입력 차단
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                        .changes.forEach { it.consume() }
                                }
                            }
                    ) {
                        SplashScreen(
                            onNavigateToHome        = { splashVisible = false },
                            onNavigateToLogin       = { splashVisible = false },
                            onNavigateToOnboarding  = { splashVisible = false }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppRootPreview() {
    AppRoot()
}
