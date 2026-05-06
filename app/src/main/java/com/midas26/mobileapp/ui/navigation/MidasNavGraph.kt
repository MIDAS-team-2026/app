package com.midas26.mobileapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.midas26.mobileapp.ui.auth.LoginScreen
import com.midas26.mobileapp.ui.auth.SignupInfoScreen
import com.midas26.mobileapp.ui.auth.SignupRoleScreen
import com.midas26.mobileapp.ui.home.HomeScreen
import com.midas26.mobileapp.ui.onboarding.OnboardingScreen
import com.midas26.mobileapp.ui.onboarding.SplashScreen
import com.midas26.mobileapp.util.PrefsManager

/**
 * zip 의 nav_graph.xml 을 1:1 로 옮긴 Compose Navigation 그래프.
 *
 * 주요 액션 맵:
 *  - splash → onboarding/login/home (popUpTo splash inclusive)
 *  - onboarding → login (popUpTo onboarding inclusive)
 *  - login → home (popUpTo login inclusive) / signup_role
 *  - signup_role → signup_info/{role}
 *  - signup_info → login (popUpTo signup_role inclusive)
 */
@Composable
fun MidasNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Splash
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.Home) {
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
                    navController.navigate(Routes.Home) {
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.SignupRole) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Home) {
            HomeScreen()
        }
    }
}
