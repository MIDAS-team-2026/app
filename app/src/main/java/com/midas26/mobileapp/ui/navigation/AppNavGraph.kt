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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.midas26.mobileapp.ui.analysis.LinguisticMarkerDetailScreen
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
import com.midas26.mobileapp.ui.tutorial.TutorialReplayTarget
import com.midas26.mobileapp.ui.tutorial.TutorialViewModel
import com.midas26.mobileapp.ui.voicechat.VoiceChatDisconnectedScreen
import com.midas26.mobileapp.ui.voicechat.VoiceChatScreen
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.delay

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
    Routes.LinguisticMarkerDetail,
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

    val tutorialViewModel: TutorialViewModel = viewModel()
    val tutorialState by tutorialViewModel.state.collectAsState()

    val prefs = PrefsManager.from(context)
    val role = prefs.getUserRole()
    val isGuardian = role == PrefsManager.ROLE_GUARDIAN
    val isPatient = role == PrefsManager.ROLE_USER
    val homeRoute = if (isGuardian) Routes.GuardianHome else Routes.UserHome

    var hasAttemptedTutorialAutoStart by remember {
        mutableStateOf(false)
    }

    /*
     * 설정에서 사용자가 선택한 다시 보기 대상을 임시로 보관합니다.
     *
     * null이면 다시 보기 요청이 없는 상태입니다.
     * 튜토리얼 완료 기록은 변경하지 않습니다.
     */
    var pendingReplayTarget by remember {
        mutableStateOf<TutorialReplayTarget?>(null)
    }

    LaunchedEffect(
        currentRoute,
        isPatient,
        tutorialState.isRunning
    ) {
        if (
            isPatient &&
            currentRoute == Routes.UserHome &&
            !tutorialState.isRunning &&
            !hasAttemptedTutorialAutoStart
        ) {
            hasAttemptedTutorialAutoStart = true

            if (prefs.shouldStartTutorial()) {
                tutorialViewModel.startFullTutorial()
            }
        }
    }

    /*
     * 설정에서 선택한 화면으로 이동이 완료된 뒤 해당 튜토리얼을 시작합니다.
     *
     * 화면 전환 직후 바로 ViewModel 상태를 변경하면 대상 화면의 Composable과
     * 하이라이트 Bounds가 아직 준비되지 않은 상태일 수 있으므로 잠시 기다립니다.
     *
     * 다시 보기에서는 PrefsManager의 최초 튜토리얼 완료 기록을 변경하지 않습니다.
     */
    LaunchedEffect(
        currentRoute,
        pendingReplayTarget,
        isPatient
    ) {
        val target = pendingReplayTarget ?: return@LaunchedEffect

        if (!isPatient) {
            pendingReplayTarget = null
            return@LaunchedEffect
        }

        val targetRoute = when (target) {
            TutorialReplayTarget.FULL,
            TutorialReplayTarget.HOME -> Routes.UserHome

            TutorialReplayTarget.VOICE_CHAT -> Routes.VoiceChat
            TutorialReplayTarget.ANALYSIS -> Routes.AnalysisResult
            TutorialReplayTarget.SETTINGS -> Routes.Settings
        }

        if (currentRoute != targetRoute) {
            return@LaunchedEffect
        }

        /*
         * 대상 화면이 한 번 구성되고 onGloballyPositioned가 실행될 시간을 확보합니다.
         * 이 지연은 화면만 이동하고 튜토리얼이 보이지 않는 현상을 줄여줍니다.
         */
        delay(180)

        /*
         * 대기 중 사용자가 다른 요청을 선택한 경우 오래된 요청을 실행하지 않습니다.
         */
        if (pendingReplayTarget != target || currentRoute != targetRoute) {
            return@LaunchedEffect
        }

        pendingReplayTarget = null
        hasAttemptedTutorialAutoStart = true
        tutorialViewModel.startReplayTutorial(target)
    }

    val selectedTab: TabId = when (currentRoute) {
        Routes.UserHome, Routes.GuardianHome ->
            if (isGuardian) GuardianHomeTab.Home else UserHomeTab.Home

        Routes.VoiceChat, Routes.VoiceChatDisconnected ->
            UserHomeTab.Chat

        Routes.AnalysisResult,
        Routes.AnalysisUserSelect,
        Routes.LinguisticMarkerDetail ->
            if (isGuardian) GuardianHomeTab.Analysis else UserHomeTab.Analysis

        Routes.LocationList,
        Routes.LocationDetail,
        Routes.LocationRoute ->
            GuardianHomeTab.Location

        Routes.Settings,
        Routes.AccessibilitySettings,
        Routes.ProfileEdit,
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
                        /*
                         * 사용자가 하단 탭을 직접 누른 경우:
                         * 1. 대기 중인 다시 보기 요청을 취소하고
                         * 2. 실행 중인 튜토리얼을 종료합니다.
                         *
                         * 튜토리얼 내부의 '다음' 버튼을 통한 화면 이동은
                         * 이 하단 탭 콜백을 거치지 않으므로 영향을 받지 않습니다.
                         */
                        pendingReplayTarget = null

                        if (tutorialState.isRunning) {
                            tutorialViewModel.stopTutorial()
                        }

                        val destination = when (tabId) {
                            UserHomeTab.Home -> Routes.UserHome
                            UserHomeTab.Chat -> Routes.VoiceChat
                            UserHomeTab.Analysis -> Routes.AnalysisResult
                            UserHomeTab.Settings -> Routes.Settings

                            GuardianHomeTab.Home -> Routes.GuardianHome
                            GuardianHomeTab.Analysis -> Routes.AnalysisUserSelect
                            GuardianHomeTab.Location -> Routes.LocationList
                            GuardianHomeTab.Settings -> Routes.Settings
                        }

                        navController.navigate(destination) {
                            popUpTo(homeRoute) {
                                inclusive = false
                            }
                            launchSingleTop = true
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
                            hasAttemptedTutorialAutoStart = false

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
                        authViewModel = authViewModel,
                        onBack = { navController.popBackStack() },
                        onVerified = {
                            authViewModel.clearCodeSent()
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
                            navController.navigate(Routes.SignupComplete) {
                                popUpTo(Routes.Login) { inclusive = true }
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
                        tutorialViewModel = tutorialViewModel,
                        userName = PrefsManager.from(context).getUserName(),
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

                    LaunchedEffect(guardianId) {
                        guardianViewModel.loadPatients(guardianId)
                    }

                    LaunchedEffect(patients) {
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
                    val user = linkedUsers.find { it.id == userId }
                        ?: linkedUsers.firstOrNull()
                        ?: return@composable

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
                    val user = linkedUsers.find { it.id == userId }
                        ?: linkedUsers.firstOrNull()
                        ?: return@composable

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
                            tutorialViewModel = tutorialViewModel,
                            userName = prefs.getUserName(),
                            weeklyScore = if (analysisViewModel.hasTodayData) analysisViewModel.displayScore else 0,
                            streakDays = analysisViewModel.streakDays,
                            onBack = {
                                navController.popBackStackIfCurrent(Routes.Settings)
                            },
                            onLogout = {
                                pendingReplayTarget = null
                                tutorialViewModel.stopTutorial()
                                hasAttemptedTutorialAutoStart = false

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
                            },
                            onReplayTutorial = { target ->
                                /*
                                 * 다시 보기 실행 전 기존 튜토리얼 상태를 정리합니다.
                                 * 최초 완료 기록은 변경하지 않습니다.
                                 */
                                tutorialViewModel.stopTutorial()
                                hasAttemptedTutorialAutoStart = true

                                val destination = when (target) {
                                    TutorialReplayTarget.FULL,
                                    TutorialReplayTarget.HOME -> Routes.UserHome

                                    TutorialReplayTarget.VOICE_CHAT -> Routes.VoiceChat
                                    TutorialReplayTarget.ANALYSIS -> Routes.AnalysisResult
                                    TutorialReplayTarget.SETTINGS -> Routes.Settings
                                }

                                if (currentRoute == destination) {
                                    /*
                                     * 현재 화면의 사용법을 선택한 경우에는 화면 이동이 없으므로
                                     * pending 상태를 남기지 않고 즉시 시작합니다.
                                     */
                                    pendingReplayTarget = null
                                    tutorialViewModel.startReplayTutorial(target)
                                } else {
                                    /*
                                     * 다른 화면의 사용법을 선택한 경우:
                                     * 1. 다시 보기 대상을 저장하고
                                     * 2. 해당 화면으로 이동한 뒤
                                     * 3. 상단 LaunchedEffect에서 화면 구성이 끝난 후 시작합니다.
                                     */
                                    pendingReplayTarget = target

                                    navController.navigate(destination) {
                                        popUpTo(Routes.UserHome) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
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
                        onBack = {
                            navController.popBackStack()
                        }
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
                        onBack = {
                            navController.popBackStack()
                        },
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
                        tutorialViewModel = tutorialViewModel,
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
                        },
                        onNavigateHome = {
                            analysisViewModel.refresh()

                            navController.navigate(Routes.UserHome) {
                                popUpTo(Routes.UserHome) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                        onNavigateAnalysis = {
                            /*
                             * 전체 튜토리얼의 음성 대화 단계가 끝나면
                             * 분석 결과 화면으로 이동합니다.
                             *
                             * TutorialViewModel의 currentScreen은
                             * VoiceChatScreen에서 ANALYSIS로 먼저 변경됩니다.
                             */
                            analysisViewModel.refresh()

                            navController.navigate(Routes.AnalysisResult) {
                                launchSingleTop = true
                            }
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

                    LaunchedEffect(patients) {
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
                        tutorialViewModel = tutorialViewModel,
                        onBack = {
                            analysisViewModel.resetToSelf()
                            navController.popBackStackIfCurrent(Routes.AnalysisResult)
                        },
                        onNavigateSettings = {
                            /*
                             * 전체 튜토리얼의 분석 결과 단계가 끝나면
                             * 설정 화면으로 이동합니다.
                             *
                             * TutorialViewModel의 currentScreen은
                             * AnalysisResultScreen에서 SETTINGS로 먼저 변경됩니다.
                             */
                            navController.navigate(Routes.Settings) {
                                launchSingleTop = true
                            }
                        },
                        viewModel = analysisViewModel,
                        onNavigateTextScoreDetail = {
                            navController.navigate(Routes.LinguisticMarkerDetail)
                        }
                    )
                }

                composable(Routes.LinguisticMarkerDetail) {
                    LinguisticMarkerDetailScreen(
                        onBack = {
                            navController.popBackStackIfCurrent(Routes.LinguisticMarkerDetail)
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