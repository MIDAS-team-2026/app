package com.midas26.mobileapp.ui.navigation

import com.midas26.mobileapp.ui.theme.AppColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.midas26.mobileapp.ui.analysis.AnalysisResultScreen
import com.midas26.mobileapp.ui.analysis.AnalysisUserSelectScreen
import com.midas26.mobileapp.ui.analysis.AnalysisViewModel
import com.midas26.mobileapp.ui.auth.AuthViewModel
import com.midas26.mobileapp.ui.auth.ForgotPasswordScreen
import com.midas26.mobileapp.ui.auth.ForgotPasswordVerifyScreen
import com.midas26.mobileapp.ui.auth.LoginFormScreen
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
import com.midas26.mobileapp.ui.guardian.GuardianViewModel
import com.midas26.mobileapp.ui.home.GuardianHomeScreen
import com.midas26.mobileapp.ui.home.GuardianMenu
import com.midas26.mobileapp.ui.home.UserHomeScreen
import com.midas26.mobileapp.ui.home.UserMenu
import com.midas26.mobileapp.ui.legal.PrivacyScreen
import com.midas26.mobileapp.ui.onboarding.OnboardingScreen
import com.midas26.mobileapp.ui.permission.PermissionScreen
import com.midas26.mobileapp.ui.settings.AccessibilitySettingsScreen
import com.midas26.mobileapp.ui.settings.GuardianAccessibilitySettingsScreen
import com.midas26.mobileapp.ui.settings.GuardianSettingsScreen
import com.midas26.mobileapp.ui.settings.ManagedUserScreen
import com.midas26.mobileapp.ui.settings.ProfileEditScreen
import com.midas26.mobileapp.ui.settings.SettingsScreen
import com.midas26.mobileapp.ui.settings.WithdrawScreen
import com.midas26.mobileapp.ui.settings.WithdrawVerifyScreen
import com.midas26.mobileapp.ui.theme.FontSizeLevel
import com.midas26.mobileapp.ui.voicechat.VoiceChatDisconnectedScreen
import com.midas26.mobileapp.ui.voicechat.VoiceChatScreen
import com.midas26.mobileapp.util.PrefsManager

private const val GuardianManagedUsersRoute = "guardian_managed_users"

private val mainRoutes = setOf(
    Routes.UserHome,
    Routes.GuardianHome,
    Routes.VoiceChat,
    Routes.VoiceChatDisconnected,
    Routes.AnalysisResult,
    Routes.Settings,
    Routes.AccessibilitySettings,
    Routes.ProfileEdit,
    Routes.LocationList,
    Routes.LocationDetail,
    Routes.LocationRoute,
    Routes.AnalysisUserSelect,
    GuardianManagedUsersRoute
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Login,
    analysisViewModel: AnalysisViewModel = viewModel(
        LocalActivity.current as ComponentActivity
    ),
    onFontSizeChange: (FontSizeLevel) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onHapticChange: (Boolean) -> Unit = {},
    onTapToReplayChange: (Boolean) -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    onVoiceChatEnabledChange: (Boolean) -> Unit = {},
    onPreviewTts: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val authViewModel: AuthViewModel = viewModel()
    val guardianViewModel: GuardianViewModel = viewModel(
        LocalActivity.current as ComponentActivity
    )

    val role = PrefsManager.from(context).getUserRole()
    val isGuardian = role == PrefsManager.ROLE_GUARDIAN
    val homeRoute = if (isGuardian) Routes.GuardianHome else Routes.UserHome

    val selectedTab: TabId = when (currentRoute) {
        Routes.UserHome, Routes.GuardianHome ->
            if (isGuardian) GuardianHomeTab.Home else UserHomeTab.Home

        Routes.VoiceChat, Routes.VoiceChatDisconnected ->
            UserHomeTab.Chat

        Routes.AnalysisResult,
        Routes.AnalysisUserSelect ->
            if (isGuardian) GuardianHomeTab.Analysis else UserHomeTab.Analysis

        Routes.LocationList,
        Routes.LocationDetail,
        Routes.LocationRoute ->
            GuardianHomeTab.Location

        Routes.Settings,
        Routes.AccessibilitySettings,
        GuardianManagedUsersRoute ->
            if (isGuardian) GuardianHomeTab.Settings else UserHomeTab.Settings

        else ->
            if (isGuardian) GuardianHomeTab.Home else UserHomeTab.Home
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in mainRoutes && PrefsManager.from(context).isLoggedIn()) {
                AppBottomBar(
                    tabs = if (isGuardian) guardianTabs else userTabs,
                    selectedTab = selectedTab,
                    onTabClick = { tabId ->
                        val dest = when (tabId) {
                            UserHomeTab.Home -> Routes.UserHome
                            UserHomeTab.Chat -> Routes.VoiceChat
                            UserHomeTab.Analysis -> Routes.AnalysisResult
                            UserHomeTab.Settings -> Routes.Settings

                            GuardianHomeTab.Home -> Routes.GuardianHome
                            GuardianHomeTab.Analysis -> Routes.AnalysisUserSelect
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
                    accent = if (isGuardian) AppColor.guardianDark else AppColor.greenSecondary
                )
            }
        }
    ) { innerPadding ->

        val visibleEntries by navController.visibleEntries.collectAsState()
        val isTransitioning = visibleEntries.size > 1

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(Routes.Onboarding) {
                    OnboardingScreen(
                        onFinish = {
                            navController.navigate(Routes.OnboardingPermission) {
                                popUpTo(Routes.Onboarding) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.OnboardingPermission) {
                    PermissionScreen(
                        onNext = {
                            navController.navigate(Routes.Login) {
                                popUpTo(Routes.OnboardingPermission) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.Login) {
                    LoginScreen(
                        onNavigateToLoginForm = {
                            navController.navigate(Routes.LoginForm)
                        },
                        onNavigateToSignup = {
                            navController.navigate(Routes.SignupRole)
                        },
                        onAccessibility = {
                            navController.navigate(Routes.LoginAccessibilitySettings)
                        }
                    )
                }

                composable(Routes.LoginForm) {
                    LoginFormScreen(
                        onNavigateToHome = {
                            analysisViewModel.refresh()
                            val home =
                                if (PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN) {
                                    Routes.GuardianHome
                                } else {
                                    Routes.UserHome
                                }

                            navController.navigate(home) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onForgotPassword = {
                            navController.navigate(Routes.ForgotPassword)
                        }
                    )
                }

                composable(Routes.ForgotPassword) {
                    ForgotPasswordScreen(
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.ForgotPassword)
                        },
                        onCodeSent = { phone ->
                            navController.navigate(Routes.forgotPasswordVerify(phone))
                        }
                    )
                }

                composable(
                    route = Routes.ForgotPasswordVerify,
                    arguments = listOf(
                        navArgument(Routes.ForgotPasswordVerifyArgPhone) {
                            type = NavType.StringType
                        }
                    )
                ) { back ->
                    val phone = back.arguments?.getString(Routes.ForgotPasswordVerifyArgPhone) ?: ""

                    ForgotPasswordVerifyScreen(
                        phone = phone,
                        onBack = { navController.popBackStack() },
                        onVerified = { verifiedPhone ->
                            navController.navigate(Routes.resetPassword(verifiedPhone)) {
                                popUpTo(Routes.ForgotPassword) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = Routes.ResetPassword,
                    arguments = listOf(
                        navArgument(Routes.ResetPasswordArgPhone) {
                            type = NavType.StringType
                        }
                    )
                ) { back ->
                    val phone = back.arguments?.getString(Routes.ResetPasswordArgPhone) ?: ""

                    ResetPasswordScreen(
                        phone = phone,
                        onBack = { navController.popBackStack() },
                        onPasswordReset = {
                            navController.navigate(Routes.Login) {
                                popUpTo(Routes.Login) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.SignupRole) {
                    SignupRoleScreen(
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.SignupRole)
                        },
                        onNext = { signupRole ->
                            navController.navigate(Routes.signupInfo(signupRole))
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
                ) { back ->
                    val signupRole = back.arguments?.getString(Routes.SignupInfoArgRole)
                        ?: PrefsManager.ROLE_USER

                    SignupInfoScreen(
                        role = signupRole,
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.SignupInfo)
                        },
                        onVerify = { phone ->
                            navController.navigate(Routes.phoneVerification(phone, signupRole))
                        },
                        viewModel = authViewModel
                    )
                }

                composable(
                    route = Routes.PhoneVerification,
                    arguments = listOf(
                        navArgument(Routes.PhoneVerificationArgPhone) {
                            type = NavType.StringType
                        },
                        navArgument(Routes.PhoneVerificationArgRole) {
                            type = NavType.StringType
                            defaultValue = PrefsManager.ROLE_USER
                        }
                    )
                ) { back ->
                    val phone = back.arguments?.getString(Routes.PhoneVerificationArgPhone) ?: ""
                    val signupRole = back.arguments?.getString(Routes.PhoneVerificationArgRole)
                        ?: PrefsManager.ROLE_USER

                    PhoneVerificationScreen(
                        phone = phone,
                        onBack = { navController.popBackStack() },
                        onVerified = {
                            navController.navigate(Routes.privacy(signupRole)) {
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
                ) { back ->
                    val permissionRole = back.arguments?.getString(Routes.PermissionArgRole)
                        ?: PrefsManager.ROLE_USER

                    PermissionScreen(
                        onNext = {
                            navController.navigate(Routes.privacy(permissionRole))
                        }
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
                ) { back ->
                    val privacyRole = back.arguments?.getString(Routes.PrivacyArgRole)
                        ?: PrefsManager.ROLE_USER

                    PrivacyScreen(
                        onAgreeAndStart = {
                            if (privacyRole == PrefsManager.ROLE_GUARDIAN) {
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
                        onGoHome = {
                            navController.navigate(Routes.Login) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.UserHome) {
                    UserHomeScreen(
                        userName = PrefsManager.from(context).getUserName(),
                        weeklyScore = if (analysisViewModel.hasTodayData) analysisViewModel.displayScore else 0,
                        streakDays = analysisViewModel.streakDays,
                        weeklyChecks = analysisViewModel.weeklyChecks,
                        weeklyDayLabels = analysisViewModel.weeklyDayLabels,
                        todayIndex = analysisViewModel.todayDayIndex,
                        onMenuClick = { menu ->
                            when (menu) {
                                UserMenu.VoiceChat -> navController.navigate(Routes.VoiceChat)
                                UserMenu.Recall -> { /* 미구현 */ }
                                UserMenu.Analysis -> navController.navigate(Routes.AnalysisResult)
                                UserMenu.Settings -> navController.navigate(Routes.Settings)
                            }
                        }
                    )
                }

                composable(Routes.GuardianHome) {
                    val prefs = PrefsManager.from(context)
                    val guardianId = prefs.getUserId()
                    val guardianName = prefs.getUserName()
                    val patients by guardianViewModel.patients.collectAsState()
                    val isLoadingPatients by guardianViewModel.isLoading.collectAsState()
                    val patientScores by guardianViewModel.patientScores.collectAsState()

                    androidx.compose.runtime.LaunchedEffect(guardianId) {
                        guardianViewModel.loadPatients(guardianId)
                    }
                    androidx.compose.runtime.LaunchedEffect(patients) {
                        if (patients.isNotEmpty()) guardianViewModel.loadPatientStatuses()
                    }

                    GuardianHomeScreen(
                        guardianName = guardianName,
                        patients = patients,
                        isLoading = isLoadingPatients,
                        patientScores = patientScores,
                        onMenuClick = { menu ->
                            when (menu) {
                                GuardianMenu.Analysis -> navController.navigate(Routes.AnalysisUserSelect)

                                GuardianMenu.Location -> {
                                    navController.navigate(Routes.LocationList) {
                                        launchSingleTop = true
                                    }
                                }

                                GuardianMenu.Settings -> navController.navigate(Routes.Settings)
                            }
                        }
                    )
                }

                composable(Routes.LocationList) {
                    val patients by guardianViewModel.patients.collectAsState()
                    val linkedUsers = patients.map { it.toLinkedUser() }

                    LocationListScreen(
                        users = linkedUsers,
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.LocationList)
                        },
                        onUserClick = { user ->
                            navController.navigate(Routes.locationDetail(user.id))
                        }
                    )
                }

                composable(
                    route = Routes.LocationDetail,
                    arguments = listOf(
                        navArgument(Routes.LocationDetailArgUserId) {
                            type = NavType.StringType
                        }
                    )
                ) { back ->
                    val userId = back.arguments?.getString(Routes.LocationDetailArgUserId) ?: ""
                    val patients by guardianViewModel.patients.collectAsState()
                    val linkedUsers = patients.map { it.toLinkedUser() }
                    val user = linkedUsers.find { it.id == userId } ?: linkedUsers.firstOrNull() ?: return@composable

                    LocationDetailScreen(
                        user = user,
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.LocationDetail)
                        },
                        onRouteClick = {
                            navController.navigate(Routes.locationRoute(user.id))
                        }
                    )
                }

                composable(
                    route = Routes.LocationRoute,
                    arguments = listOf(
                        navArgument(Routes.LocationRouteArgUserId) {
                            type = NavType.StringType
                        }
                    )
                ) { back ->
                    val userId = back.arguments?.getString(Routes.LocationRouteArgUserId) ?: ""
                    val patients by guardianViewModel.patients.collectAsState()
                    val linkedUsers = patients.map { it.toLinkedUser() }
                    val user = linkedUsers.find { it.id == userId } ?: linkedUsers.firstOrNull() ?: return@composable

                    LocationRouteScreen(
                        user = user,
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.LocationRoute)
                        }
                    )
                }

                composable(Routes.Settings) {
                    if (PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN) {
                        val patients by guardianViewModel.patients.collectAsState()
                        val patientStatuses by guardianViewModel.patientStatuses.collectAsState()

                        LaunchedEffect(patients) {
                            if (patients.isNotEmpty()) guardianViewModel.loadPatientStatuses()
                        }

                        val noResultCount = patientStatuses.values.count {
                            it == com.midas26.mobileapp.ui.guardian.PatientAnalysisStatus.NO_RESULT
                        }
                        val unviewedCount = patientStatuses.values.count {
                            it == com.midas26.mobileapp.ui.guardian.PatientAnalysisStatus.NEW_RESULT
                        }

                        GuardianSettingsScreen(
                            userName = PrefsManager.from(context).getUserName(),
                            patients = patients,
                            noResultCount = noResultCount,
                            unviewedCount = unviewedCount,
                            onBack = {
                                navController.popBackStackIfCurrent(Routes.Settings)
                            },
                            onLogout = {
                                navController.navigate(Routes.Login) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onDeleteAccount = {
                                navController.navigate(Routes.Withdraw)
                            },
                            onAccessibility = {
                                navController.navigate(Routes.AccessibilitySettings)
                            },
                            onManagedUsers = {
                                navController.navigate(GuardianManagedUsersRoute)
                            },
                            onProfileEdit = {
                                navController.navigate(Routes.ProfileEdit)
                            }
                        )
                    } else {
                        SettingsScreen(
                            userName = PrefsManager.from(context).getUserName(),
                            weeklyScore = if (analysisViewModel.hasTodayData) analysisViewModel.displayScore else 0,
                            streakDays = analysisViewModel.streakDays,
                            onBack = {
                                navController.popBackStackIfCurrent(Routes.Settings)
                            },
                            onLogout = {
                                navController.navigate(Routes.Login) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onDeleteAccount = {
                                navController.navigate(Routes.Withdraw)
                            },
                            onAccessibility = {
                                navController.navigate(Routes.AccessibilitySettings)
                            },
                            onProfileEdit = {
                                navController.navigate(Routes.ProfileEdit)
                            }
                        )
                    }
                }

                composable(GuardianManagedUsersRoute) {
                    val patients by guardianViewModel.patients.collectAsState()
                    val isLoadingPatients by guardianViewModel.isLoading.collectAsState()

                    ManagedUserScreen(
                        patients = patients,
                        isLoading = isLoadingPatients,
                        viewModel = guardianViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ProfileEdit) {
                    val prefs = PrefsManager.from(context)
                    val patients by guardianViewModel.patients.collectAsState()

                    ProfileEditScreen(
                        initialName = prefs.getUserName(),
                        initialPhone = prefs.getUserPhone(),
                        linkedPatients = if (prefs.getUserRole() == PrefsManager.ROLE_GUARDIAN) patients else null,
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.ProfileEdit)
                        }
                    )
                }

                composable(Routes.Withdraw) {
                    WithdrawScreen(
                        phone = PrefsManager.from(context).getUserPhone(),
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.Withdraw)
                        },
                        onSendCode = {
                            val phone = PrefsManager.from(context).getUserPhone()
                            navController.navigate(Routes.withdrawVerify(phone))
                        }
                    )
                }

                composable(
                    route = Routes.WithdrawVerify,
                    arguments = listOf(
                        navArgument(Routes.WithdrawVerifyArgPhone) {
                            type = NavType.StringType
                        }
                    )
                ) { back ->
                    val phone = back.arguments?.getString(Routes.WithdrawVerifyArgPhone) ?: ""

                    WithdrawVerifyScreen(
                        phone = phone,
                        onBack = { navController.popBackStack() },
                        onWithdrawn = {
                            navController.navigate(Routes.Login) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.AccessibilitySettings) {
                    if (PrefsManager.from(context).getUserRole() == PrefsManager.ROLE_GUARDIAN) {
                        GuardianAccessibilitySettingsScreen(
                            onBack = {
                                navController.popBackStackIfCurrent(Routes.AccessibilitySettings)
                            },
                            onFontSizeChange = onFontSizeChange,
                            onHighContrastChange = onHighContrastChange,
                            onHapticChange = onHapticChange
                        )
                    } else {
                        AccessibilitySettingsScreen(
                            onBack = {
                                navController.popBackStackIfCurrent(Routes.AccessibilitySettings)
                            },
                            onFontSizeChange = onFontSizeChange,
                            onHighContrastChange = onHighContrastChange,
                            onHapticChange = onHapticChange,
                            onTapToReplayChange = onTapToReplayChange,
                            onSpeedChange = onSpeedChange,
                            onVoiceChatEnabledChange = onVoiceChatEnabledChange,
                            onPreviewTts = onPreviewTts
                        )
                    }
                }

                composable(Routes.LoginAccessibilitySettings) {
                    GuardianAccessibilitySettingsScreen(
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.LoginAccessibilitySettings)
                        },
                        onFontSizeChange = onFontSizeChange,
                        onHighContrastChange = onHighContrastChange,
                        onHapticChange = onHapticChange
                    )
                }

                composable(Routes.VoiceChat) {
                    VoiceChatScreen(
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.VoiceChat)
                        },
                        onDisconnected = {
                            navController.navigate(Routes.VoiceChatDisconnected)
                        },
                        onNavigateToSettings = {
                            navController.navigate(Routes.AccessibilitySettings)
                        },
                        onSessionEnded = {
                            analysisViewModel.refresh()
                        }
                    )
                }

                composable(Routes.VoiceChatDisconnected) {
                    VoiceChatDisconnectedScreen(
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.VoiceChatDisconnected)
                        },
                        onGoHome = {
                            navController.navigate(Routes.UserHome) {
                                popUpTo(Routes.UserHome) { inclusive = true }
                            }
                        },
                        onRetry = {
                            navController.popBackStackIfCurrent(Routes.VoiceChatDisconnected)
                        }
                    )
                }

                composable(Routes.AnalysisUserSelect) {
                    val patients by guardianViewModel.patients.collectAsState()
                    val isLoadingPatients by guardianViewModel.isLoading.collectAsState()
                    val patientStatuses by guardianViewModel.patientStatuses.collectAsState()

                    // 환자 목록이 준비되면 상태 조회 시작
                    androidx.compose.runtime.LaunchedEffect(patients) {
                        if (patients.isNotEmpty()) guardianViewModel.loadPatientStatuses()
                    }

                    AnalysisUserSelectScreen(
                        patients = patients,
                        isLoading = isLoadingPatients,
                        patientStatuses = patientStatuses,
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.AnalysisUserSelect)
                        },
                        onUserClick = { patient ->
                            patient.userId?.let { patientId ->
                                guardianViewModel.markPatientViewed(patientId)
                                analysisViewModel.loadForPatient(patientId)
                                navController.navigate(Routes.AnalysisResult)
                            }
                        }
                    )
                }

                composable(Routes.AnalysisResult) {
                    AnalysisResultScreen(
                        onBack = {
                            analysisViewModel.resetToSelf()
                            navController.popBackStackIfCurrent(Routes.AnalysisResult)
                        },
                        viewModel = analysisViewModel
                    )
                }
            }

            if (isTransitioning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                }
                            }
                        }
                )
            }
        }
    }
}