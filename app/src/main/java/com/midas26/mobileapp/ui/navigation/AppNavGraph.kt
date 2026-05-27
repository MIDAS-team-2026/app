package com.midas26.mobileapp.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.midas26.mobileapp.ui.analysis.AnalysisGraphScreen
import com.midas26.mobileapp.ui.analysis.AnalysisLoadingScreen
import com.midas26.mobileapp.ui.analysis.AnalysisResultScreen
import com.midas26.mobileapp.ui.auth.AuthViewModel
import com.midas26.mobileapp.ui.auth.ForgotPasswordScreen
import com.midas26.mobileapp.ui.auth.ForgotPasswordVerifyScreen
import com.midas26.mobileapp.ui.auth.LoginScreen
import com.midas26.mobileapp.ui.auth.PhoneVerificationScreen
import com.midas26.mobileapp.ui.auth.ResetPasswordScreen
import com.midas26.mobileapp.ui.auth.SignupCompleteScreen
import com.midas26.mobileapp.ui.auth.SignupInfoScreen
import com.midas26.mobileapp.ui.auth.SignupRoleScreen
import com.midas26.mobileapp.ui.components.AppBottomBar
import com.midas26.mobileapp.ui.components.GuardianHomeTab
import com.midas26.mobileapp.ui.components.TabId
import com.midas26.mobileapp.ui.components.UserHomeTab
import com.midas26.mobileapp.ui.components.guardianTabs
import com.midas26.mobileapp.ui.components.userTabs
import com.midas26.mobileapp.ui.home.GuardianHomeScreen
import com.midas26.mobileapp.ui.home.GuardianMenu
import com.midas26.mobileapp.ui.home.UserHomeScreen
import com.midas26.mobileapp.ui.home.UserMenu
import com.midas26.mobileapp.ui.legal.PrivacyScreen
import com.midas26.mobileapp.ui.onboarding.OnboardingScreen
import com.midas26.mobileapp.ui.permission.PermissionScreen
import com.midas26.mobileapp.ui.recall.RecallQuestionScreen
import com.midas26.mobileapp.ui.recall.RecallResultScreen
import com.midas26.mobileapp.ui.recall.RecallStartScreen
import com.midas26.mobileapp.ui.settings.AccessibilitySettingsScreen
import com.midas26.mobileapp.ui.settings.ProfileEditScreen
import com.midas26.mobileapp.ui.settings.SettingsScreen
import com.midas26.mobileapp.ui.settings.WithdrawScreen
import com.midas26.mobileapp.ui.settings.WithdrawVerifyScreen
import com.midas26.mobileapp.ui.theme.FontSizeLevel
import com.midas26.mobileapp.ui.theme.Green500
import com.midas26.mobileapp.ui.theme.GuardianAccentDark
import com.midas26.mobileapp.ui.voicechat.VoiceChatDisconnectedScreen
import com.midas26.mobileapp.ui.voicechat.VoiceChatScreen
import com.midas26.mobileapp.util.PrefsManager
import com.midas26.mobileapp.ui.navigation.LocationListScreen
import com.midas26.mobileapp.ui.navigation.LocationDetailScreen
import com.midas26.mobileapp.ui.navigation.LocationRouteScreen
import com.midas26.mobileapp.ui.navigation.sampleUsers

// ── 메인 라우트 목록 (하단 바 표시 여부 판단용) ──────────────────────────────
private val mainRoutes = setOf(
    Routes.UserHome, Routes.GuardianHome,
    Routes.VoiceChat, Routes.VoiceChatDisconnected,
    Routes.RecallStart, Routes.RecallQuestion, Routes.RecallResult,
    Routes.AnalysisLoading, Routes.AnalysisResult, Routes.AnalysisGraph,
    Routes.Settings, Routes.AccessibilitySettings, Routes.ProfileEdit,
    // ── 위치 정보 화면들도 하단 바 유지 ──
    Routes.LocationList, Routes.LocationDetail, Routes.LocationRoute
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Login,
    onFontSizeChange: (FontSizeLevel) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onHapticChange: (Boolean) -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    onVoiceChatEnabledChange: (Boolean) -> Unit = {},
    onPreviewTts: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // 회원가입 화면들 간 공유 ViewModel (Activity 스코프)
    val authViewModel: AuthViewModel = viewModel()

    val role      = PrefsManager.from(context).getUserRole()
    val isGuardian = role == PrefsManager.ROLE_GUARDIAN
    val homeRoute  = if (isGuardian) Routes.GuardianHome else Routes.UserHome

    val selectedTab: TabId = when (currentRoute) {
        Routes.UserHome, Routes.GuardianHome ->
            if (isGuardian) GuardianHomeTab.Home else UserHomeTab.Home
        Routes.VoiceChat, Routes.VoiceChatDisconnected -> UserHomeTab.Chat
        Routes.RecallStart, Routes.RecallQuestion, Routes.RecallResult,
        Routes.AnalysisLoading, Routes.AnalysisResult, Routes.AnalysisGraph ->
            if (isGuardian) GuardianHomeTab.Analysis else UserHomeTab.Analysis
        // ── 위치 화면들은 Location 탭 선택 상태 유지 ──
        Routes.LocationList, Routes.LocationDetail, Routes.LocationRoute ->
            GuardianHomeTab.Location
        Routes.Settings, Routes.AccessibilitySettings ->
            if (isGuardian) GuardianHomeTab.Settings else UserHomeTab.Settings
        else -> if (isGuardian) GuardianHomeTab.Home else UserHomeTab.Home
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in mainRoutes) {
                AppBottomBar(
                    tabs        = if (isGuardian) guardianTabs else userTabs,
                    selectedTab = selectedTab,
                    onTabClick  = { tabId ->
                        val dest = when (tabId) {
                            UserHomeTab.Home      -> Routes.UserHome
                            UserHomeTab.Chat      -> Routes.VoiceChat
                            UserHomeTab.Analysis  -> Routes.AnalysisResult
                            UserHomeTab.Settings  -> Routes.Settings
                            GuardianHomeTab.Home     -> Routes.GuardianHome
                            GuardianHomeTab.Analysis -> Routes.AnalysisResult
                            // ── 위치 탭 클릭 → LocationList(화면 1)로 이동 ──
                            GuardianHomeTab.Location -> Routes.LocationList
                            GuardianHomeTab.Settings -> Routes.Settings
                            else -> null
                        }
                        dest?.let {
                            navController.navigate(it) {
                                popUpTo(homeRoute) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    accent = if (isGuardian) GuardianAccentDark else Green500
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding)
        ) {

            // ── 온보딩 ────────────────────────────────────────────────────
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.OnboardingPermission) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
                    }
                )
            }

            // ── 온보딩 후 권한 설명 화면 ──────────────────────────────────
            composable(Routes.OnboardingPermission) {
                PermissionScreen(
                    onNext = {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.OnboardingPermission) { inclusive = true }
                        }
                    }
                )
            }

            // ── 로그인 ────────────────────────────────────────────────────
            composable(Routes.Login) {
                LoginScreen(
                    onNavigateToHome = {
                        val home = if (PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN)
                            Routes.GuardianHome else Routes.UserHome
                        navController.navigate(home) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    },
                    onNavigateToSignup = { navController.navigate(Routes.SignupRole) },
                    onForgotPassword   = { navController.navigate(Routes.ForgotPassword) }
                )
            }

            // ── 비밀번호 찾기 ──────────────────────────────────────────────
            composable(Routes.ForgotPassword) {
                ForgotPasswordScreen(
                    onBack     = { navController.popBackStackIfCurrent(Routes.ForgotPassword) },
                    onCodeSent = { phone -> navController.navigate(Routes.forgotPasswordVerify(phone)) }
                )
            }
            composable(
                route = Routes.ForgotPasswordVerify,
                arguments = listOf(navArgument(Routes.ForgotPasswordVerifyArgPhone) { type = NavType.StringType })
            ) { back ->
                val phone = back.arguments?.getString(Routes.ForgotPasswordVerifyArgPhone) ?: ""
                ForgotPasswordVerifyScreen(
                    phone      = phone,
                    onBack     = { navController.popBackStack() },
                    onVerified = { verifiedPhone ->
                        navController.navigate(Routes.resetPassword(verifiedPhone)) {
                            popUpTo(Routes.ForgotPassword) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Routes.ResetPassword,
                arguments = listOf(navArgument(Routes.ResetPasswordArgPhone) { type = NavType.StringType })
            ) { back ->
                val phone = back.arguments?.getString(Routes.ResetPasswordArgPhone) ?: ""
                ResetPasswordScreen(
                    phone           = phone,
                    onBack          = { navController.popBackStack() },
                    onPasswordReset = {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    }
                )
            }

            // ── 회원가입 ──────────────────────────────────────────────────
            composable(Routes.SignupRole) {
                SignupRoleScreen(
                    onBack = { navController.popBackStackIfCurrent(Routes.SignupRole) },
                    onNext = { role -> navController.navigate(Routes.signupInfo(role)) }
                )
            }
            composable(
                route = Routes.SignupInfo,
                arguments = listOf(navArgument(Routes.SignupInfoArgRole) {
                    type = NavType.StringType; defaultValue = PrefsManager.ROLE_USER })
            ) { back ->
                val role = back.arguments?.getString(Routes.SignupInfoArgRole) ?: PrefsManager.ROLE_USER
                SignupInfoScreen(
                    role      = role,
                    onBack    = { navController.popBackStackIfCurrent(Routes.SignupInfo) },
                    onVerify  = { phone -> navController.navigate(Routes.phoneVerification(phone, role)) },
                    viewModel = authViewModel
                )
            }
            composable(
                route = Routes.PhoneVerification,
                arguments = listOf(
                    navArgument(Routes.PhoneVerificationArgPhone) { type = NavType.StringType },
                    navArgument(Routes.PhoneVerificationArgRole)  { type = NavType.StringType; defaultValue = PrefsManager.ROLE_USER }
                )
            ) { back ->
                val phone = back.arguments?.getString(Routes.PhoneVerificationArgPhone) ?: ""
                val role  = back.arguments?.getString(Routes.PhoneVerificationArgRole) ?: PrefsManager.ROLE_USER
                PhoneVerificationScreen(
                    phone      = phone,
                    onBack     = { navController.popBackStack() },
                    onVerified = {
                        navController.navigate(Routes.privacy(role)) {
                            popUpTo(Routes.SignupRole) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Routes.Permission,
                arguments = listOf(navArgument(Routes.PermissionArgRole) {
                    type = NavType.StringType; defaultValue = PrefsManager.ROLE_USER })
            ) { back ->
                val role = back.arguments?.getString(Routes.PermissionArgRole) ?: PrefsManager.ROLE_USER
                PermissionScreen(onNext = { navController.navigate(Routes.privacy(role)) })
            }
            composable(
                route = Routes.Privacy,
                arguments = listOf(navArgument(Routes.PrivacyArgRole) {
                    type = NavType.StringType; defaultValue = PrefsManager.ROLE_USER })
            ) { back ->
                val role = back.arguments?.getString(Routes.PrivacyArgRole) ?: PrefsManager.ROLE_USER
                PrivacyScreen(
                    onAgreeAndStart = {
                        if (role == PrefsManager.ROLE_GUARDIAN) {
                            navController.navigate(Routes.GuardianHome) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.SignupComplete) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(Routes.SignupComplete) {
                SignupCompleteScreen(
                    viewModel = authViewModel,
                    onGoHome  = {
                        navController.navigate(Routes.Login) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ── 사용자 홈 ──────────────────────────────────────────────────
            composable(Routes.UserHome) {
                UserHomeScreen(
                    userName    = PrefsManager.from(context).getUserName(),
                    onMenuClick = { menu ->
                        when (menu) {
                            UserMenu.VoiceChat -> navController.navigate(Routes.VoiceChat)
                            UserMenu.Recall    -> navController.navigate(Routes.RecallStart)
                            UserMenu.Analysis  -> navController.navigate(Routes.AnalysisLoading)
                            UserMenu.Settings  -> navController.navigate(Routes.Settings)
                        }
                    }
                )
            }

            // ── 보호자 홈 ──────────────────────────────────────────────────
            composable(Routes.GuardianHome) {
                GuardianHomeScreen(
                    onMenuClick = { menu ->
                        when (menu) {
                            GuardianMenu.Analysis -> navController.navigate(Routes.AnalysisResult)
                            // ── 위치 정보 카드 → 화면 1(사용자 목록) ──
                            GuardianMenu.Location -> navController.navigate(Routes.LocationList) {
                                launchSingleTop = true
                            }
                            GuardianMenu.Info     -> { /* TODO: 관련 정보 화면 */ }
                            GuardianMenu.Settings -> navController.navigate(Routes.Settings)
                        }
                    }
                )
            }

            // ══════════════════════════════════════════════════════════════
            // 위치 정보 화면 1 — 사용자 목록 (사진 1번)
            // ══════════════════════════════════════════════════════════════
            composable(Routes.LocationList) {
                LocationListScreen(
                    users  = sampleUsers,
                    onBack = { navController.popBackStackIfCurrent(Routes.LocationList) },
                    onUserClick = { user ->
                        navController.navigate(Routes.locationDetail(user.id))
                    }
                )
            }

            // 위치 정보 화면 2 — 지도 + 이동경로/전화걸기 (사진 2번)
            composable(
                route = Routes.LocationDetail,
                arguments = listOf(navArgument(Routes.LocationDetailArgUserId) { type = NavType.StringType })
            ) { back ->
                val userId = back.arguments?.getString(Routes.LocationDetailArgUserId) ?: ""
                val user   = sampleUsers.find { it.id == userId } ?: sampleUsers.first()

                LocationDetailScreen(
                    user        = user,
                    onBack      = { navController.popBackStackIfCurrent(Routes.LocationDetail) },
                    onRouteClick = {
                        // 이동 경로 탭
                        navController.navigate(Routes.locationRoute(user.id))
                    }
                )
            }

            // 이동 경로 + 타임라인
            composable(
                route = Routes.LocationRoute,
                arguments = listOf(navArgument(Routes.LocationRouteArgUserId) { type = NavType.StringType })
            ) { back ->
                val userId = back.arguments?.getString(Routes.LocationRouteArgUserId) ?: ""
                val user   = sampleUsers.find { it.id == userId } ?: sampleUsers.first()

                LocationRouteScreen(
                    user   = user,
                    onBack = { navController.popBackStackIfCurrent(Routes.LocationRoute) }
                )
            }

            // ── 설정 ──────────────────────────────────────────────────────
            composable(Routes.Settings) {
                SettingsScreen(
                    userName  = PrefsManager.from(context).getUserName(),
                    onBack    = { navController.popBackStackIfCurrent(Routes.Settings) },
                    onLogout  = {
                        navController.navigate(Routes.Login) { popUpTo(0) { inclusive = true } }
                    },
                    onDeleteAccount  = { navController.navigate(Routes.Withdraw) },
                    onAccessibility  = { navController.navigate(Routes.AccessibilitySettings) },
                    onProfileEdit    = { navController.navigate(Routes.ProfileEdit) }
                )
            }
            composable(Routes.ProfileEdit) {
                ProfileEditScreen(
                    initialName  = PrefsManager.from(context).getUserName(),
                    initialPhone = PrefsManager.from(context).getUserPhone(),
                    onBack       = { navController.popBackStackIfCurrent(Routes.ProfileEdit) }
                )
            }
            composable(Routes.Withdraw) {
                WithdrawScreen(
                    phone    = PrefsManager.from(context).getUserPhone(),
                    onBack   = { navController.popBackStackIfCurrent(Routes.Withdraw) },
                    onSendCode = {
                        val phone = PrefsManager.from(context).getUserPhone()
                        navController.navigate(Routes.withdrawVerify(phone))
                    }
                )
            }
            composable(
                route = Routes.WithdrawVerify,
                arguments = listOf(navArgument(Routes.WithdrawVerifyArgPhone) { type = NavType.StringType })
            ) { back ->
                val phone = back.arguments?.getString(Routes.WithdrawVerifyArgPhone) ?: ""
                WithdrawVerifyScreen(
                    phone      = phone,
                    onBack     = { navController.popBackStack() },
                    onWithdrawn = {
                        navController.navigate(Routes.Login) { popUpTo(0) { inclusive = true } }
                    }
                )
            }
            composable(Routes.AccessibilitySettings) {
                AccessibilitySettingsScreen(
                    onBack               = { navController.popBackStackIfCurrent(Routes.AccessibilitySettings) },
                    onFontSizeChange     = onFontSizeChange,
                    onHighContrastChange = onHighContrastChange,
                    onHapticChange       = onHapticChange,
                    onSpeedChange        = onSpeedChange,
                    onVoiceChatEnabledChange = onVoiceChatEnabledChange,
                    onPreviewTts         = onPreviewTts
                )
            }

            // ── 음성 대화 ──────────────────────────────────────────────────
            composable(Routes.VoiceChat) {
                VoiceChatScreen(
                    onBack        = { navController.popBackStackIfCurrent(Routes.VoiceChat) },
                    onDisconnected = { navController.navigate(Routes.VoiceChatDisconnected) }
                )
            }
            composable(Routes.VoiceChatDisconnected) {
                VoiceChatDisconnectedScreen(
                    onBack   = { navController.popBackStackIfCurrent(Routes.VoiceChatDisconnected) },
                    onGoHome = {
                        navController.navigate(Routes.UserHome) {
                            popUpTo(Routes.UserHome) { inclusive = true }
                        }
                    },
                    onRetry = { navController.popBackStackIfCurrent(Routes.VoiceChatDisconnected) }
                )
            }

            // ── 회상 과제 ──────────────────────────────────────────────────
            composable(Routes.RecallStart) {
                RecallStartScreen(
                    onBack  = { navController.popBackStackIfCurrent(Routes.RecallStart) },
                    onStart = {
                        navController.navigate(Routes.RecallQuestion) {
                            popUpTo(Routes.RecallStart) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.RecallQuestion) {
                RecallQuestionScreen(
                    onBack       = { navController.popBackStackIfCurrent(Routes.RecallQuestion) },
                    onFinishedAll = {
                        navController.navigate(Routes.RecallResult) {
                            popUpTo(Routes.RecallQuestion) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.RecallResult) {
                RecallResultScreen(
                    onBack      = { navController.popBackStackIfCurrent(Routes.RecallResult) },
                    onGoHome    = {
                        navController.navigate(Routes.UserHome) {
                            popUpTo(Routes.UserHome) { inclusive = true }
                        }
                    },
                    onSeeDetails = { navController.navigate(Routes.AnalysisLoading) }
                )
            }

            // ── 분석 ──────────────────────────────────────────────────────
            composable(Routes.AnalysisLoading) {
                AnalysisLoadingScreen(
                    onFinished = {
                        navController.navigate(Routes.AnalysisResult) {
                            popUpTo(Routes.AnalysisLoading) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.AnalysisResult) {
                AnalysisResultScreen(
                    onBack      = { navController.popBackStackIfCurrent(Routes.AnalysisResult) },
                    onShowGraph = { navController.navigate(Routes.AnalysisGraph) }
                )
            }
            composable(Routes.AnalysisGraph) {
                AnalysisGraphScreen(
                    onBack = { navController.popBackStackIfCurrent(Routes.AnalysisGraph) }
                )
            }

        } // NavHost
    } // Scaffold
}