package com.midas26.mobileapp.ui.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.midas26.mobileapp.network.LocationRepository
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

data class GpsState(
    val latitude: Double = 37.5700,
    val longitude: Double = 126.9820,
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    val errorMsg: String = ""
)

val sampleUsers = listOf(
    LinkedUser(
        id = "1",
        name = "홍길동",
        relation = "부",
        address = "서울특별시 종로구 종로 1가",
        phone = "010-1234-5678",
        lastUpdatedMin = 5,
        totalDistanceKm = 2.4,
        visitedPlaces = 3,
        travelHours = 4,
        timeline = listOf(
            TimelineItem("14:32", "현 위치", "서울시 종로구 삼봉동 123", true),
            TimelineItem("12:10", "병원", "서울시 종로구 율곡로 456"),
            TimelineItem("10:05", "공원", "서울시 종로구 창경궁로 789")
        ),
        latitude = 37.5700,
        longitude = 126.9820,
        routePoints = listOf(
            GeoPoint(37.5668, 126.9780),
            GeoPoint(37.5682, 126.9795),
            GeoPoint(37.5691, 126.9808),
            GeoPoint(37.5700, 126.9820)
        )
    ),
    LinkedUser(
        id = "2",
        name = "박순임",
        relation = "모",
        address = "서울특별시 종로구 사직동 18",
        phone = "010-9876-5432",
        lastUpdatedMin = 12,
        totalDistanceKm = 1.1,
        visitedPlaces = 2,
        travelHours = 2,
        timeline = listOf(
            TimelineItem("13:45", "현 위치", "서울시 서대문구 연희동 456", true),
            TimelineItem("11:20", "약국", "서울시 서대문구 홍제동 789")
        ),
        latitude = 37.5760,
        longitude = 126.9368,
        routePoints = listOf(
            GeoPoint(37.5738, 126.9340),
            GeoPoint(37.5746, 126.9350),
            GeoPoint(37.5760, 126.9368)
        )
    )
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListScreen(
    users: List<LinkedUser> = sampleUsers,
    onBack: () -> Unit,
    onUserClick: (LinkedUser) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 24.sp)

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "GPS 위치 확인",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF9E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📡", fontSize = 26.sp)

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "실시간 위치를 확인하세요",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB8860B)
                        )
                    }
                }

                users.forEach { user ->
                    UserLocationCard(
                        user = user,
                        onClick = { onUserClick(user) }
                    )
                }
            }

            VerticalScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun UserLocationCard(
    user: LinkedUser,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(150.dp)
            .clickable { onClick() }
            .border(
                width = 1.5.dp,
                color = Color(0xFFB3D4F5),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${user.name}  (${user.relation})",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 18.sp)

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = user.address,
                        fontSize = 18.sp,
                        color = AppColor.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕐", fontSize = 17.sp)

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "${user.lastUpdatedMin}분 전 업데이트",
                        fontSize = 17.sp,
                        color = AppColor.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColor.textTertiary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    user: LinkedUser,
    onBack: () -> Unit,
    onRouteClick: () -> Unit
) {
    val context = LocalContext.current
    var showCallDialog by remember { mutableStateOf(false) }

    val userId = user.id.toIntOrNull() ?: 0

    var serverLatitude by remember { mutableStateOf(user.latitude) }
    var serverLongitude by remember { mutableStateOf(user.longitude) }
    var isLoadingLocation by remember { mutableStateOf(true) }
    var locationError by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        isLoadingLocation = true
        locationError = ""

        LocationRepository.getCurrentLocation(userId)
            .onSuccess { location ->
                if (location != null) {
                    serverLatitude = location.latitude
                    serverLongitude = location.longitude
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
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "위치 정보",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    isLoadingLocation -> {
                        LoadingBar()
                    }

                    locationError.isNotBlank() -> {
                        StatusBar(locationError, isError = true)
                    }

                    else -> {
                        StatusBar("서버에서 최신 위치 조회 완료", isError = false)
                    }
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
                            .clickable { onRouteClick() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${user.name}님 이동 경로",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColor.textPrimary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clickable { showCallDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${user.name}님 전화 걸기",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColor.textPrimary
                            )
                        }
                    }
                }
            }
        }

        if (showCallDialog) {
            CallDialog(
                user = user,
                onDismiss = { showCallDialog = false }
            )
        }
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
                minZoomLevel = 4.0
                maxZoomLevel = 20.0
                controller.setZoom(17.0)
                controller.setCenter(currentPoint)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            if (routePoints.size >= 2) {
                mapView.overlays.add(
                    Polyline().apply {
                        setPoints(routePoints)
                        outlinePaint.color = 0xFF1689D9.toInt()
                        outlinePaint.strokeWidth = 13f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                )
            }

            mapView.overlays.add(
                Polygon().apply {
                    points = Polygon.pointsAsCircle(currentPoint, 85.0)
                    fillColor = 0x334CAF50
                    strokeColor = 0xFF2FA84F.toInt()
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
                    fillColor = 0xFF2FA84F.toInt()
                    strokeColor = 0xFF1E7F3A.toInt()
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
private fun StatusBar(
    text: String,
    isError: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isError) Color(0xFFFCEEED) else Color(0xFFF0F7EC)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (isError) Color(0xFFD9534F) else AppColor.greenSecondary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                fontSize = 12.sp,
                color = if (isError) Color(0xFFD9534F) else AppColor.accentDark
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationRouteScreen(
    user: LinkedUser,
    onBack: () -> Unit
) {
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
    }

    val userId = user.id.toIntOrNull() ?: 0

    var routePoints by remember { mutableStateOf(user.routePoints) }
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
                    Text(
                        text = "위치 정보",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    isLoadingRoute -> {
                        LoadingBar()
                    }

                    routeError.isNotBlank() -> {
                        StatusBar(routeError, isError = true)
                    }

                    else -> {
                        StatusBar("서버에서 이동 경로 조회 완료", isError = false)
                    }
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

                SummaryRow("총 경로", "${user.totalDistanceKm} km")
                SummaryRow("방문 장소", "${user.visitedPlaces} 곳")
                SummaryRow("이동 시간", "${user.travelHours} 시간")

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "타임 라인",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column {
                    user.timeline.forEachIndexed { idx, item ->
                        TimelineRow(
                            item = item,
                            isLast = idx == user.timeline.size - 1
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F7EC)
                ) {
                    Text(
                        text = "📍 위치는 5분마다 자동으로 업데이트돼요",
                        fontSize = 13.sp,
                        color = AppColor.accentDark,
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
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0F7EC),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )

            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
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
                color = if (item.isCurrent) AppColor.greenSecondary else Color(0xFFB0CCA0)
            ) {}

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(Color(0xFFB0CCA0))
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
                    color = if (item.isCurrent) AppColor.greenSecondary else AppColor.textPrimary
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
fun CallDialog(
    user: LinkedUser,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            context.startActivity(
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse("tel:${user.phone.replace("-", "")}")
                )
            )
        }
    }

    fun makeCall() {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            context.startActivity(
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse("tel:${user.phone.replace("-", "")}")
                )
            )
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp
            ),
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
                        .background(
                            Color(0xFFDDDDDD),
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${user.name}님께 전화 걸기",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F7EC),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${user.name}님",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColor.textPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = user.phone,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColor.textPrimary,
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
                            .height(54.dp)
                            .clickable { makeCall() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "전화 연결",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColor.textPrimary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "취소",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColor.textTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

sealed class LocationScreenState {
    object List : LocationScreenState()
    data class Detail(val user: LinkedUser) : LocationScreenState()
    data class Route(val user: LinkedUser) : LocationScreenState()
}