package com.midas26.mobileapp.ui.navigation

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.midas26.mobileapp.util.PrefsManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.midas26.mobileapp.network.LocationRepository
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.LocalFontSizeScale
import com.midas26.mobileapp.ui.tutorial.TutorialOverlay
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianLocationTutorialStep
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialScreen
import com.midas26.mobileapp.ui.tutorial.guardian.GuardianTutorialViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val GuardianTutorialAccentColor = Color(0xFFC85E48)

private const val GPS_REFRESH_INTERVAL_MS = 5 * 60 * 1000L


data class LinkedUser(
    val id: String,
    val name: String,
    val relation: String,
    val address: String,
    val phone: String,
    val lastUpdatedMin: Int,
    val totalDistanceKm: Double,
    val visitedPlaces: Int,
    val travelHours: Int,
    val timeline: List<TimelineItem>,
    val latitude: Double = 37.5700,
    val longitude: Double = 126.9820,
    val routePoints: List<GeoPoint> = emptyList()
)

data class TimelineItem(
    val time: String,
    val label: String,
    val address: String,
    val isCurrent: Boolean = false
)

fun com.midas26.mobileapp.network.LinkedUserInfo.toLinkedUser() = LinkedUser(
    id = userId?.toString() ?: "",
    name = name ?: "이름 없음",
    relation = "",
    address = "",
    phone = phone ?: "",
    lastUpdatedMin = 0,
    totalDistanceKm = 0.0,
    visitedPlaces = 0,
    travelHours = 0,
    timeline = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListScreen(
    guardianTutorialViewModel: GuardianTutorialViewModel,
    users: List<LinkedUser>,
    onBack: () -> Unit,
    onUserClick: (LinkedUser) -> Unit
) {
    val tutorialState by guardianTutorialViewModel.state.collectAsState()

    /*
     * 앱 설정에서 선택한 글자 크기를 위치 사용자 선택 화면에도 적용합니다.
     * 화면 폭이 좁을 때 레이아웃이 깨지지 않도록 최대 배율을 제한합니다.
     */
    val fontScale = LocalFontSizeScale.current.scale
    val screenFontScale = fontScale.coerceAtMost(1.35f)

    var refreshKey by remember { mutableStateOf(0) }
    var guideBounds by remember { mutableStateOf<Rect?>(null) }
    var firstUserBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(GPS_REFRESH_INTERVAL_MS)
            refreshKey++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GPS 위치 확인",
                        fontWeight = FontWeight.Bold,
                        fontSize = (22 * screenFontScale).sp,
                        color = AppColor.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            guideBounds = coordinates.boundsInRoot()
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFFE5DF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFC85E48),
                            modifier = Modifier.size(30.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "실시간 위치를 확인하세요!",
                                fontSize = (20 * screenFontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC85E48),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "위치는 5분마다 갱신됩니다",
                                fontSize = (14 * screenFontScale).sp,
                                color = Color(0xFFC85E48).copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                users.forEachIndexed { index, user ->
                    UserLocationCard(
                        user = user,
                        refreshKey = refreshKey,
                        fontScale = fontScale,
                        onBoundsChanged = { bounds ->
                            if (index == 0) {
                                firstUserBounds = bounds
                            }
                        },
                        onClick = {
                            onUserClick(user)
                        }
                    )
                }
            }

            VerticalScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            if (
                tutorialState.isRunning &&
                tutorialState.currentScreen ==
                GuardianTutorialScreen.LOCATION_SELECT &&
                tutorialState.locationStep ==
                GuardianLocationTutorialStep.USER_SELECT
            ) {
                val targetBounds =
                    firstUserBounds
                val firstUser = users.firstOrNull()

                if (targetBounds != null && firstUser != null) {
                    val openFirstUserLocation = {
                        guardianTutorialViewModel.moveToLocationDetail(
                            number = tutorialState.currentNumber + 1
                        )
                        onUserClick(firstUser)
                    }

                    TutorialOverlay(
                        targetBounds = targetBounds,
                        title = "위치를 확인할 사용자 선택",
                        message =
                            "연결된 사용자 중 현재 위치를 확인할 사용자를 선택해 보세요.",
                        currentStep =
                            tutorialState.currentNumber,
                        totalSteps =
                            tutorialState.totalNumber,
                        onNext = openFirstUserLocation,
                        onTargetClick = openFirstUserLocation,
                        onSkip = {
                            guardianTutorialViewModel.stopTutorial()
                        },
                        tutorialColor = GuardianTutorialAccentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun UserLocationCard(
    user: LinkedUser,
    refreshKey: Int = 0,
    fontScale: Float,
    onBoundsChanged: (Rect) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val userId = user.id.toIntOrNull() ?: 0
    val relation =
        if (userId > 0) PrefsManager.from(context).getPatientRelation(userId).ifEmpty { "사용자" }
        else "사용자"
    /*
     * 사용자 위치 카드는 최대 1.25배까지만 확대해
     * 이름, 관계, 주소가 잘리지 않도록 합니다.
     */
    val cardFontScale = fontScale.coerceAtMost(1.25f)
    val cardMinHeight = (126f + ((cardFontScale - 1f) * 72f)).dp
    val pillFontSize = (14 * cardFontScale).sp

    var locationText by remember { mutableStateOf("위치 불러오는 중...") }
    var timeAgoText by remember { mutableStateOf("") }

    LaunchedEffect(userId, refreshKey) {
        if (userId <= 0) {
            locationText = "위치 정보 없음"
            timeAgoText = ""
            return@LaunchedEffect
        }

        LocationRepository.getCurrentLocation(userId)
            .onSuccess { loc ->
                if (loc != null) {
                    val apiAddress = getDisplayAddressFromApiObject(loc)
                    val convertedAddress = apiAddress.ifBlank {
                        getAddressFromLatLng(
                            context = context,
                            latitude = loc.latitude,
                            longitude = loc.longitude
                        )
                    }

                    locationText = convertedAddress.ifBlank {
                        "%.4f, %.4f".format(loc.latitude, loc.longitude)
                    }

                    timeAgoText = formatTimeAgo(loc.recordedAt)
                } else {
                    locationText = "위치 정보 없음"
                    timeAgoText = ""
                }
            }
            .onFailure {
                locationText = "위치 조회 실패"
                timeAgoText = ""
            }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .heightIn(min = cardMinHeight)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(
                    coordinates.boundsInRoot()
                )
            }
            .clickable { onClick() }
            .border(1.5.dp, Color(0xFFC85E48), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        fontSize = (25 * cardFontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = AppColor.guardianSurface,
                        modifier = Modifier.wrapContentHeight()
                    ) {
                        Text(
                            text = relation,
                            fontSize = pillFontSize,
                            lineHeight = pillFontSize,
                            fontWeight = FontWeight.Medium,
                            color = AppColor.guardianDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("📍", fontSize = (15 * cardFontScale).sp)

                    Spacer(modifier = Modifier.width(5.dp))

                    Text(
                        text = locationText,
                        fontSize = (14 * cardFontScale).sp,
                        color = AppColor.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (18 * cardFontScale).sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (timeAgoText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "🕐 $timeAgoText 업데이트",
                        fontSize = (14 * cardFontScale).sp,
                        color = AppColor.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColor.textTertiary,
                modifier = Modifier.size((26 * cardFontScale.coerceAtMost(1.15f)).dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    guardianTutorialViewModel: GuardianTutorialViewModel,
    user: LinkedUser,
    onBack: () -> Unit,
    onRouteClick: () -> Unit
) {
    val tutorialState by guardianTutorialViewModel.state.collectAsState()
    val context = LocalContext.current

    var showCallDialog by remember { mutableStateOf(false) }
    var mapBounds by remember { mutableStateOf<Rect?>(null) }
    var routeButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var callButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var callDialogBounds by remember { mutableStateOf<Rect?>(null) }

    val userId = user.id.toIntOrNull() ?: 0

    var serverLatitude by remember { mutableStateOf(user.latitude) }
    var serverLongitude by remember { mutableStateOf(user.longitude) }
    var isLoadingLocation by remember { mutableStateOf(true) }
    var locationError by remember { mutableStateOf("") }
    var locationTimeAgo by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(GPS_REFRESH_INTERVAL_MS)
            refreshKey++
        }
    }

    LaunchedEffect(userId, refreshKey) {
        isLoadingLocation = true
        locationError = ""
        locationTimeAgo = ""

        LocationRepository.getCurrentLocation(userId)
            .onSuccess { location ->
                if (location != null) {
                    serverLatitude = location.latitude
                    serverLongitude = location.longitude
                    locationTimeAgo = formatTimeAgo(location.recordedAt)
                } else {
                    locationError = "저장된 위치가 아직 없어요"
                }
            }
            .onFailure {
                locationError = "서버 위치 조회에 실패했어요"
            }

        isLoadingLocation = false
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("위치 정보", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { refreshKey++ },
                            enabled = !isLoadingLocation
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "새로고침",
                                tint = if (isLoadingLocation) Color.Black.copy(alpha = 0.3f) else Color.Black
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color.White
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    isLoadingLocation -> LoadingBar()
                    locationError.isNotBlank() -> StatusBar(locationError, isError = true)
                    else -> StatusBar("$locationTimeAgo 위치 정보를 가져왔습니다.", isError = false)
                }

                HighlightLocationMap(
                    latitude = serverLatitude,
                    longitude = serverLongitude,
                    routePoints = user.routePoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .onGloballyPositioned { coordinates ->
                            mapBounds = coordinates.boundsInRoot()
                        }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .onGloballyPositioned { coordinates ->
                                routeButtonBounds =
                                    coordinates.boundsInRoot()
                            }
                            .clickable { onRouteClick() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFE5DF),
                        border = BorderStroke(
                            width = 1.0.dp,
                            color = Color(0xFFC85E48)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${user.name}님 이동 경로",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .onGloballyPositioned { coordinates ->
                                callButtonBounds =
                                    coordinates.boundsInRoot()
                            }
                            .clickable { showCallDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFE5DF),
                        border = BorderStroke(
                            width = 1.0.dp,
                            color = Color(0xFFC85E48)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${user.name}님 전화 걸기",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        if (showCallDialog) {
            CallDialog(
                user = user,
                onDismiss = {
                    showCallDialog = false
                },
                onBoundsChanged = {
                    callDialogBounds = it
                }
            )
        }

        val isLocationDetailTutorialRunning =
            tutorialState.isRunning &&
                    tutorialState.currentScreen ==
                    GuardianTutorialScreen.LOCATION_DETAIL &&
                    tutorialState.locationStep in setOf(
                GuardianLocationTutorialStep.CURRENT_LOCATION,
                GuardianLocationTutorialStep.ROUTE_BUTTON,
                GuardianLocationTutorialStep.CALL_BUTTON,
                GuardianLocationTutorialStep.CALL_DIALOG
            )

        if (isLocationDetailTutorialRunning) {
            val targetBounds = when (tutorialState.locationStep) {
                GuardianLocationTutorialStep.CURRENT_LOCATION ->
                    mapBounds

                GuardianLocationTutorialStep.ROUTE_BUTTON ->
                    routeButtonBounds

                GuardianLocationTutorialStep.CALL_BUTTON ->
                    callButtonBounds

                GuardianLocationTutorialStep.CALL_DIALOG ->
                    callDialogBounds

                GuardianLocationTutorialStep.USER_SELECT,
                GuardianLocationTutorialStep.ROUTE_RESULT,
                GuardianLocationTutorialStep.MOVE_TO_SETTINGS_TAB,
                GuardianLocationTutorialStep.COMPLETED ->
                    null
            }

            if (targetBounds != null) {
                val title = when (tutorialState.locationStep) {
                    GuardianLocationTutorialStep.CURRENT_LOCATION ->
                        "현재 위치 확인"

                    GuardianLocationTutorialStep.ROUTE_BUTTON ->
                        "이동 경로 확인"

                    GuardianLocationTutorialStep.CALL_BUTTON ->
                        "전화 걸기"

                    GuardianLocationTutorialStep.CALL_DIALOG ->
                        "전화 연결 화면"

                    else -> ""
                }

                val message = when (tutorialState.locationStep) {
                    GuardianLocationTutorialStep.CURRENT_LOCATION ->
                        "지도에서 선택한 사용자의 현재 위치를 확인할 수 있어요. \n위치는 5분마다 갱신돼요!"

                    GuardianLocationTutorialStep.ROUTE_BUTTON ->
                        "이 버튼에서 오늘 이동한 경로와 방문 장소를 확인할 수 있어요."

                    GuardianLocationTutorialStep.CALL_BUTTON ->
                        "버튼을 누르면 사용자에게 전화할 수 있는 화면이 열려요."

                    GuardianLocationTutorialStep.CALL_DIALOG ->
                        "전화번호를 확인하고 전화 연결 버튼으로 통화를 시작할 수 있어요."

                    else -> ""
                }

                val advanceLocationTutorial = {
                    when (tutorialState.locationStep) {
                        GuardianLocationTutorialStep.CURRENT_LOCATION -> {
                            guardianTutorialViewModel.moveToLocationDetail(
                                step = GuardianLocationTutorialStep.ROUTE_BUTTON,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        GuardianLocationTutorialStep.ROUTE_BUTTON -> {
                            guardianTutorialViewModel.moveToLocationRoute(
                                number = tutorialState.currentNumber + 1
                            )
                            onRouteClick()
                        }

                        GuardianLocationTutorialStep.CALL_BUTTON -> {
                            showCallDialog = true
                            guardianTutorialViewModel.moveToLocationDetail(
                                step = GuardianLocationTutorialStep.CALL_DIALOG,
                                number = tutorialState.currentNumber + 1
                            )
                        }

                        GuardianLocationTutorialStep.CALL_DIALOG -> {
                            showCallDialog = false

                            if (
                                guardianTutorialViewModel
                                    .isFullTutorial()
                            ) {
                                guardianTutorialViewModel
                                    .showSettingsTabGuide(
                                        number =
                                            tutorialState.currentNumber + 1
                                    )
                            } else {
                                guardianTutorialViewModel
                                    .completeTutorial()
                            }
                        }

                        else -> Unit
                    }
                }

                TutorialOverlay(
                    targetBounds = targetBounds,
                    title = title,
                    message = message,
                    currentStep = tutorialState.currentNumber,
                    totalSteps =
                        tutorialState.totalNumber,
                    onNext = advanceLocationTutorial,
                    onTargetClick = advanceLocationTutorial,
                    onSkip = {
                        guardianTutorialViewModel.stopTutorial()
                    },
                    tutorialColor = GuardianTutorialAccentColor
                )
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationRouteScreen(
    guardianTutorialViewModel: GuardianTutorialViewModel,
    user: LinkedUser,
    onBack: () -> Unit
) {
    val tutorialState by guardianTutorialViewModel.state.collectAsState()
    val context = LocalContext.current
    var routeScreenBounds by remember { mutableStateOf<Rect?>(null) }

    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    }

    val userId = user.id.toIntOrNull() ?: 0

    var routePoints by remember { mutableStateOf(user.routePoints) }
    var timelineItems by remember { mutableStateOf(user.timeline) }

    var totalDistanceKm by remember { mutableStateOf(user.totalDistanceKm) }
    var visitedPlaces by remember { mutableStateOf(user.visitedPlaces) }
    var travelTimeText by remember { mutableStateOf("0분") }

    var isLoadingRoute by remember { mutableStateOf(true) }
    var routeError by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        isLoadingRoute = true
        routeError = ""

        LocationRepository.getRoute(userId, today)
            .onSuccess { points ->
                routePoints = points.map {
                    GeoPoint(it.latitude, it.longitude)
                }

                totalDistanceKm = calculateTotalDistanceKm(routePoints)
                visitedPlaces = estimateVisitedPlaces(routePoints)

                val recordedTimes = points.mapNotNull {
                    runCatching { it.recordedAt }.getOrNull()
                }

                travelTimeText = calculateTravelTimeText(recordedTimes)

                val apiAddresses = points.map {
                    getDisplayAddressFromApiObject(it)
                }

                val convertedAddresses = routePoints.mapIndexed { index, point ->
                    apiAddresses.getOrNull(index).orEmpty().ifBlank {
                        getAddressFromLatLng(
                            context = context,
                            latitude = point.latitude,
                            longitude = point.longitude
                        )
                    }
                }

                val apiPlaceNames = points.map {
                    getPlaceNameFromApiObject(it)
                }

                timelineItems = if (routePoints.isNotEmpty()) {
                    buildTimelineItems(
                        points = routePoints,
                        recordedTimes = recordedTimes,
                        apiAddresses = convertedAddresses,
                        apiPlaceNames = apiPlaceNames
                    )
                } else {
                    emptyList()
                }

                if (routePoints.isEmpty()) {
                    routeError = "오늘 저장된 이동 경로가 없어요"
                }
            }
            .onFailure {
                routeError = "이동 경로 조회에 실패했어요"
            }

        isLoadingRoute = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("위치 정보", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onGloballyPositioned { coordinates ->
                    routeScreenBounds = coordinates.boundsInRoot()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isLoadingRoute -> LoadingBar()
                    routeError.isNotBlank() -> StatusBar(routeError, isError = true)
                    else -> StatusBar("서버에서 이동 경로 조회 완료", isError = false)
                }

                if (routePoints.isNotEmpty()) {
                    HighlightLocationMap(
                        latitude = routePoints.last().latitude,
                        longitude = routePoints.last().longitude,
                        routePoints = routePoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                SummaryRow("총 경로", String.format(Locale.KOREA, "%.1f km", totalDistanceKm))
                SummaryRow("방문 장소", "$visitedPlaces 곳")
                SummaryRow("이동 시간", travelTimeText)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "타임 라인",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (timelineItems.isEmpty()) {
                    Text(
                        text = "표시할 타임라인이 없어요",
                        fontSize = 14.sp,
                        color = AppColor.textTertiary
                    )
                } else {
                    Column {
                        timelineItems.forEachIndexed { idx, item ->
                            TimelineRow(
                                item = item,
                                isLast = idx == timelineItems.size - 1
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFE5DF)
                ) {
                    Text(
                        text = "위치는 5분마다 자동 업데이트 됩니다!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC85E48),
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            VerticalScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    if (
        tutorialState.isRunning &&
        tutorialState.currentScreen ==
        GuardianTutorialScreen.LOCATION_ROUTE &&
        tutorialState.locationStep ==
        GuardianLocationTutorialStep.ROUTE_RESULT &&
        routeScreenBounds != null
    ) {
        val finishRouteGuide = {
            guardianTutorialViewModel.moveToLocationDetail(
                step = GuardianLocationTutorialStep.CALL_BUTTON,
                number = tutorialState.currentNumber + 1
            )
            onBack()
        }

        TutorialOverlay(
            targetBounds = routeScreenBounds,
            title = "이동 경로 상세 확인",
            message =
                "총 경로, 방문 장소, 이동 시간과 위치 타임라인을 확인할 수 있어요.",
            currentStep = tutorialState.currentNumber,
            totalSteps = tutorialState.totalNumber,
            onNext = finishRouteGuide,
            onTargetClick = finishRouteGuide,
            onSkip = {
                guardianTutorialViewModel.stopTutorial()
            },
            tutorialColor = GuardianTutorialAccentColor
        )
    }

}

@Composable
private fun HighlightLocationMap(
    latitude: Double,
    longitude: Double,
    routePoints: List<GeoPoint>,
    modifier: Modifier = Modifier
) {
    val currentPoint = GeoPoint(latitude, longitude)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
                minZoomLevel = 4.0
                maxZoomLevel = 20.0
                controller.setZoom(17.0)
                controller.setCenter(currentPoint)

                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_POINTER_UP -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }

                    false
                }
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            if (routePoints.size >= 2) {
                mapView.overlays.add(
                    Polyline().apply {
                        setPoints(routePoints)
                        outlinePaint.color = 0xFFC85E48.toInt()
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                )
            }

            mapView.overlays.add(
                Polygon().apply {
                    points = Polygon.pointsAsCircle(currentPoint, 85.0)
                    fillColor = 0x33C85E48
                    strokeColor = 0xFFC85E48.toInt()
                    strokeWidth = 4f
                }
            )

            mapView.overlays.add(
                Polygon().apply {
                    points = Polygon.pointsAsCircle(currentPoint, 9.0)
                    fillColor = 0xFFFFFFFF.toInt()
                    strokeColor = 0xFFFFFFFF.toInt()
                    strokeWidth = 2f
                }
            )

            mapView.overlays.add(
                Polygon().apply {
                    points = Polygon.pointsAsCircle(currentPoint, 6.0)
                    fillColor = 0xFFC85E48.toInt()
                    strokeColor = 0xFFB5503D.toInt()
                    strokeWidth = 2f
                }
            )

            mapView.controller.setZoom(17.0)
            mapView.controller.animateTo(currentPoint)
            mapView.invalidate()
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFE5DF),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFC85E48)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
        }
    }
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = if (item.isCurrent) Color(0xFFC85E48) else Color(0xFFE4725B)
            ) {}

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(if (item.isCurrent) Color(0xFFC85E48) else Color(0xFFE4725B))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp)
        ) {
            Row {
                Text(
                    text = item.time,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isCurrent) Color(0xFFC85E48) else AppColor.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = item.address,
                fontSize = 13.sp,
                color = AppColor.textTertiary
            )
        }
    }
}

@Composable
private fun StatusBar(
    text: String,
    isError: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isError) Color(0xFFFCEEED) else Color(0xFFFFE5DF)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (isError) Color(0xFFD9534F) else Color(0xFFC85E48),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) Color(0xFFD9534F) else Color(0xFFC85E48)
            )
        }
    }
}

@Composable
private fun LoadingBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF0F7EC)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = AppColor.greenSecondary,
                strokeWidth = 2.dp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "GPS 위치 불러오는 중...",
                fontSize = 12.sp,
                color = AppColor.textTertiary
            )
        }
    }
}

@Composable
fun CallDialog(
    user: LinkedUser,
    onDismiss: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {}
) {
    val context = LocalContext.current

    fun makeCall() {
        context.startActivity(
            Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:${user.phone.replace("-", "")}")
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    onBoundsChanged(coordinates.boundsInRoot())
                },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color(0xFFDDDDDD), RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${user.name}님께 전화 걸기",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFE5DF),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = Color(0xFFC85E48)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${user.name}님",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = formatPhoneNumber(user.phone),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clickable { makeCall() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFE5DF),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = Color(0xFFC85E48)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "전화 연결",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF9FAFB),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = Color(0xFFE5E7EB)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "취소",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}


private fun formatPhoneNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }

    return when (digits.length) {
        11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7, 11)}"
        10 -> {
            if (digits.startsWith("02")) {
                "${digits.substring(0, 2)}-${digits.substring(2, 6)}-${digits.substring(6, 10)}"
            } else {
                "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6, 10)}"
            }
        }
        9 -> {
            if (digits.startsWith("02")) {
                "${digits.substring(0, 2)}-${digits.substring(2, 5)}-${digits.substring(5, 9)}"
            } else {
                phone
            }
        }
        else -> phone
    }
}

private fun formatTimeAgo(recordedAt: String): String {
    return try {
        val past = parseServerDate(recordedAt) ?: return "시간 알 수 없음"
        val diffSec = ((System.currentTimeMillis() - past.time) / 1000).coerceAtLeast(0)

        when {
            diffSec < 3600 -> {
                val rawMinutes = diffSec / 60
                val roundedMinutes = maxOf(5, (rawMinutes / 5) * 5)
                "${roundedMinutes}분 전"
            }

            diffSec < 86400 -> {
                val hours = diffSec / 3600
                "${hours}시간 전"
            }

            else -> {
                val days = diffSec / 86400
                "${days}일 전"
            }
        }
    } catch (e: Exception) {
        "시간 알 수 없음"
    }
}

private fun parseServerDate(value: String): Date? {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.KOREA),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.KOREA),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
    )

    for (format in formats) {
        val parsed = runCatching { format.parse(value) }.getOrNull()
        if (parsed != null) return parsed
    }

    return null
}

private fun formatRouteTime(value: String?): String {
    if (value.isNullOrBlank()) return "--:--"

    val date = parseServerDate(value) ?: return "--:--"
    return SimpleDateFormat("HH:mm", Locale.KOREA).format(date)
}

private fun calculateTotalDistanceKm(points: List<GeoPoint>): Double {
    if (points.size < 2) return 0.0

    var totalMeter = 0f

    for (i in 0 until points.size - 1) {
        val result = FloatArray(1)

        Location.distanceBetween(
            points[i].latitude,
            points[i].longitude,
            points[i + 1].latitude,
            points[i + 1].longitude,
            result
        )

        totalMeter += result[0]
    }

    return ((totalMeter / 1000.0) * 10).roundToInt() / 10.0
}

private fun calculateTravelTimeText(recordedTimes: List<String>): String {
    if (recordedTimes.size < 2) return "0분"

    val start = parseServerDate(recordedTimes.first()) ?: return "0분"
    val end = parseServerDate(recordedTimes.last()) ?: return "0분"

    val diffMillis = end.time - start.time
    if (diffMillis <= 0) return "0분"

    val totalMinutes = diffMillis / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        totalMinutes <= 0 -> "0분"
        hours <= 0 -> "${minutes}분"
        minutes <= 0 -> "${hours}시간"
        else -> "${hours}시간 ${minutes}분"
    }
}

private fun estimateVisitedPlaces(points: List<GeoPoint>): Int {
    if (points.isEmpty()) return 0
    if (points.size < 3) return 1

    var count = 1
    var lastPlace = points.first()

    points.drop(1).forEach { point ->
        val result = FloatArray(1)

        Location.distanceBetween(
            lastPlace.latitude,
            lastPlace.longitude,
            point.latitude,
            point.longitude,
            result
        )

        if (result[0] >= 300f) {
            count++
            lastPlace = point
        }
    }

    return count
}

private fun buildTimelineItems(
    points: List<GeoPoint>,
    recordedTimes: List<String>,
    apiAddresses: List<String>,
    apiPlaceNames: List<String>
): List<TimelineItem> {
    if (points.isEmpty()) return emptyList()

    val firstIndex = 0
    val lastIndex = points.lastIndex
    val middleIndex = if (points.size >= 3) points.size / 2 else -1

    val items = mutableListOf<TimelineItem>()

    items.add(
        TimelineItem(
            time = formatRouteTime(recordedTimes.getOrNull(lastIndex)),
            label = apiPlaceNames.getOrNull(lastIndex).orEmpty().ifBlank { "현 위치" },
            address = apiAddresses.getOrNull(lastIndex).orEmpty().ifBlank {
                "%.4f, %.4f".format(points[lastIndex].latitude, points[lastIndex].longitude)
            },
            isCurrent = true
        )
    )

    if (middleIndex != -1) {
        items.add(
            TimelineItem(
                time = formatRouteTime(recordedTimes.getOrNull(middleIndex)),
                label = apiPlaceNames.getOrNull(middleIndex).orEmpty().ifBlank { "이동 중" },
                address = apiAddresses.getOrNull(middleIndex).orEmpty().ifBlank {
                    "%.4f, %.4f".format(points[middleIndex].latitude, points[middleIndex].longitude)
                }
            )
        )
    }

    if (points.size >= 2) {
        items.add(
            TimelineItem(
                time = formatRouteTime(recordedTimes.getOrNull(firstIndex)),
                label = apiPlaceNames.getOrNull(firstIndex).orEmpty().ifBlank { "출발 위치" },
                address = apiAddresses.getOrNull(firstIndex).orEmpty().ifBlank {
                    "%.4f, %.4f".format(points[firstIndex].latitude, points[firstIndex].longitude)
                }
            )
        )
    }

    return items
}

private suspend fun getAddressFromLatLng(
    context: Context,
    latitude: Double,
    longitude: Double
): String {
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.KOREA)
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]

                val adminArea = address.adminArea.orEmpty()
                val locality = address.locality.orEmpty()
                val subLocality = address.subLocality.orEmpty()
                val thoroughfare = address.thoroughfare.orEmpty()
                val featureName = address.featureName.orEmpty()

                listOf(
                    adminArea,
                    locality,
                    subLocality,
                    thoroughfare,
                    featureName
                )
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}

private fun getDisplayAddressFromApiObject(obj: Any): String {
    val roadAddress = getStringProperty(
        obj = obj,
        names = listOf("roadAddress", "road_address")
    )

    val address = getStringProperty(
        obj = obj,
        names = listOf("address", "addressName", "address_name")
    )

    return roadAddress.ifBlank { address }
}

private fun getPlaceNameFromApiObject(obj: Any): String {
    return getStringProperty(
        obj = obj,
        names = listOf("placeName", "place_name", "zoneName", "zone_name")
    )
}

private fun getStringProperty(
    obj: Any,
    names: List<String>
): String {
    for (name in names) {
        val getterName = "get" + name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }

        val methodValue = runCatching {
            obj.javaClass.methods
                .firstOrNull { method ->
                    method.name == getterName && method.parameterTypes.isEmpty()
                }
                ?.invoke(obj)
                ?.toString()
                ?.trim()
        }.getOrNull()

        if (!methodValue.isNullOrBlank()) return methodValue

        val fieldValue = runCatching {
            val field = obj.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(obj)?.toString()?.trim()
        }.getOrNull()

        if (!fieldValue.isNullOrBlank()) return fieldValue
    }

    return ""
}

sealed class LocationScreenState {
    object List : LocationScreenState()
    data class Detail(val user: LinkedUser) : LocationScreenState()
    data class Route(val user: LinkedUser) : LocationScreenState()
}