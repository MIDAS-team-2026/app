package com.midas26.mobileapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.midas26.mobileapp.ui.auth.LoginScreen
import com.midas26.mobileapp.ui.auth.SignupCompleteScreen
import com.midas26.mobileapp.ui.auth.SignupInfoScreen
import com.midas26.mobileapp.ui.auth.SignupRoleScreen
import com.midas26.mobileapp.ui.home.GuardianHomeScreen
import com.midas26.mobileapp.ui.home.UserHomeScreen
import com.midas26.mobileapp.ui.legal.PrivacyScreen
import com.midas26.mobileapp.ui.onboarding.OnboardingScreen
import com.midas26.mobileapp.ui.onboarding.SplashScreen
import com.midas26.mobileapp.ui.analysis.AnalysisGraphScreen
import com.midas26.mobileapp.ui.analysis.AnalysisLoadingScreen
import com.midas26.mobileapp.ui.analysis.AnalysisResultScreen
import com.midas26.mobileapp.ui.permission.PermissionScreen
import com.midas26.mobileapp.ui.recall.RecallQuestionScreen
import com.midas26.mobileapp.ui.recall.RecallResultScreen
import com.midas26.mobileapp.ui.recall.RecallStartScreen
import com.midas26.mobileapp.ui.voicechat.VoiceChatDisconnectedScreen
import com.midas26.mobileapp.ui.voicechat.VoiceChatScreen
import com.midas26.mobileapp.ui.home.UserMenu
import com.midas26.mobileapp.util.PrefsManager

/**
 * 앱 네비게이션 그래프.
 *
 * 흐름:
 *   Splash → (Onboarding) → Login
 *   Login → SignupRole → SignupInfo/{role} → Permission/{role} → Privacy/{role}
 *           ├─ role=user     → SignupComplete → UserHome
 *           └─ role=guardian → GuardianHome
 *   Splash 진입 시 토큰 있으면 → 역할에 따라 UserHome / GuardianHome
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Routes.Splash
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onNavigateToHome = {
                    val home = if (PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN)
                        Routes.GuardianHome else Routes.UserHome
                    navController.navigate(home) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Routes.Onboarding) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Onboarding) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                onNavigateToHome = {
                    val home = if (PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN)
                        Routes.GuardianHome else Routes.UserHome
                    navController.navigate(home) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(Routes.SignupRole)
                }
            )
        }

        composable(Routes.SignupRole) {
            SignupRoleScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.SignupRole) },
                onNext = { role ->
                    navController.navigate(Routes.signupInfo(role))
                }
            )
        }

        composable(
            route = Routes.SignupInfo,
            arguments = listOf(
                navArgument(Routes.SignupInfoArgRole) {
                    type = NavType.StringType
                    defaultValue = PrefsManager.ROLE_USER
                }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString(Routes.SignupInfoArgRole)
                ?: PrefsManager.ROLE_USER
            SignupInfoScreen(
                role = role,
                onBack = { navController.popBackStackIfCurrent(Routes.SignupInfo) },
                // 회원가입 완료 후 권한 요청 화면으로 이동
                onComplete = {
                    navController.navigate(Routes.permission(role)) {
                        popUpTo(Routes.SignupRole) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.Permission,
            arguments = listOf(
                navArgument(Routes.PermissionArgRole) {
                    type = NavType.StringType
                    defaultValue = PrefsManager.ROLE_USER
                }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString(Routes.PermissionArgRole)
                ?: PrefsManager.ROLE_USER
            PermissionScreen(
                onNext = { navController.navigate(Routes.privacy(role)) }
            )
        }

        composable(
            route = Routes.Privacy,
            arguments = listOf(
                navArgument(Routes.PrivacyArgRole) {
                    type = NavType.StringType
                    defaultValue = PrefsManager.ROLE_USER
                }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString(Routes.PrivacyArgRole)
                ?: PrefsManager.ROLE_USER
            PrivacyScreen(
                onAgreeAndStart = {
                    if (role == PrefsManager.ROLE_GUARDIAN) {
                        // 보호자는 가입 완료 화면 없이 바로 보호자 홈
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
                userCode = "842716", // 더미. 실제 백엔드 연동 시 ViewModel에서 주입
                onGoHome = {
                    navController.navigate(Routes.UserHome) {
                        popUpTo(Routes.SignupComplete) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.UserHome) {
            UserHomeScreen(
                onMenuClick = { menu ->
                    when (menu) {
                        UserMenu.VoiceChat -> navController.navigate(Routes.VoiceChat)
                        UserMenu.Recall    -> navController.navigate(Routes.RecallStart)
                        UserMenu.Analysis  -> navController.navigate(Routes.AnalysisLoading)
                        // 설정은 추후 화면 작성 시 라우트 추가
                        else -> Unit
                    }
                }
            )
        }

        composable(Routes.GuardianHome) {
            GuardianHomeScreen()
        }

        composable(Routes.VoiceChat) {
            VoiceChatScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.VoiceChat) },
                onDisconnected = { navController.navigate(Routes.VoiceChatDisconnected) }
            )
        }

        composable(Routes.VoiceChatDisconnected) {
            VoiceChatDisconnectedScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.VoiceChatDisconnected) },
                onGoHome = {
                    navController.navigate(Routes.UserHome) {
                        popUpTo(Routes.UserHome) { inclusive = true }
                    }
                },
                onRetry = { navController.popBackStackIfCurrent(Routes.VoiceChatDisconnected) }
            )
        }

        composable(Routes.RecallStart) {
            RecallStartScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.RecallStart) },
                onStart = {
                    navController.navigate(Routes.RecallQuestion) {
                        popUpTo(Routes.RecallStart) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.RecallQuestion) {
            RecallQuestionScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.RecallQuestion) },
                onFinishedAll = {
                    navController.navigate(Routes.RecallResult) {
                        popUpTo(Routes.RecallQuestion) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.RecallResult) {
            RecallResultScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.RecallResult) },
                onGoHome = {
                    navController.navigate(Routes.UserHome) {
                        popUpTo(Routes.UserHome) { inclusive = true }
                    }
                },
                onSeeDetails = {
                    navController.navigate(Routes.AnalysisLoading)
                }
            )
        }

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
                onBack = { navController.popBackStackIfCurrent(Routes.AnalysisResult) },
                onShowGraph = { navController.navigate(Routes.AnalysisGraph) }
            )
        }

        composable(Routes.AnalysisGraph) {
            AnalysisGraphScreen(
                onBack = { navController.popBackStackIfCurrent(Routes.AnalysisGraph) }
            )
        }
    }
}
