package com.midas26.mobileapp.ui.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.midas26.mobileapp.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

// ══════════════════════════════════════════════════════════════════════════════
// 데이터 모델
// ══════════════════════════════════════════════════════════════════════════════
data class LinkedUser(
    val id: String,
    val name: String,
    val relation: String,       // "부", "모" 등
    val address: String,
    val phone: String,
    val lastUpdatedMin: Int,
    val totalDistanceKm: Double,
    val visitedPlaces: Int,
    val travelHours: Int,
    val timeline: List<TimelineItem>,
    val latitude: Double = 37.5700,
    val longitude: Double = 126.9820,
    val routePoints: List<GeoPoint> = emptyList(),
    val status: UserStatus = UserStatus.NORMAL  // 양호 / 주의
)

enum class UserStatus { NORMAL, WARNING }

data class TimelineItem(
    val time: String,
    val label: String,
    val address: String,
    val isCurrent: Boolean = false
)

val sampleUsers = listOf(
    LinkedUser(
        id = "1", name = "홍길동", relation = "부",
        address = "서울특별시 종로구 종로 1가",
        phone = "010-1234-5678",
        lastUpdatedMin = 5,
        totalDistanceKm = 2.4, visitedPlaces = 3, travelHours = 4,
        timeline = listOf(
            TimelineItem("14:32", "현 위치", "서울시 종로구 삼봉동 123", true),
            TimelineItem("12:10", "병원",    "서울시 종로구 율곡로 456"),
            TimelineItem("10:05", "공원",    "서울시 종로구 창경궁로 789")
        ),
        latitude = 37.5700, longitude = 126.9820,
        routePoints = listOf(
            GeoPoint(37.5668, 126.9780), GeoPoint(37.5682, 126.9795),
            GeoPoint(37.5691, 126.9808), GeoPoint(37.5700, 126.9820)
        ),
        status = UserStatus.NORMAL
    ),
    LinkedUser(
        id = "2", name = "박순임", relation = "모",
        address = "서울특별시 종로구 사직동 18",
        phone = "010-9876-5432",
        lastUpdatedMin = 12,
        totalDistanceKm = 1.1, visitedPlaces = 2, travelHours = 2,
        timeline = listOf(
            TimelineItem("13:45", "현 위치", "서울시 서대문구 연희동 456", true),
            TimelineItem("11:20", "약국",    "서울시 서대문구 홍제동 789")
        ),
        latitude = 37.5760, longitude = 126.9368,
        routePoints = listOf(
            GeoPoint(37.5738, 126.9340), GeoPoint(37.5746, 126.9350),
            GeoPoint(37.5760, 126.9368)
        ),
        status = UserStatus.WARNING
    )
)

// ══════════════════════════════════════════════════════════════════════════════
// GPS 상태
// ══════════════════════════════════════════════════════════════════════════════
data class GpsState(
    val latitude: Double = 37.5700,
    val longitude: Double = 126.9820,
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    val errorMsg: String = ""
)

@SuppressLint("MissingPermission")
@Composable
fun rememberGpsState(): GpsState {
    val context = LocalContext.current
    var gpsState by remember { mutableStateOf(GpsState()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        gpsState = gpsState.copy(
            hasPermission = granted,
            errorMsg = if (!granted) "위치 권한이 필요해요" else ""
        )
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) gpsState = gpsState.copy(hasPermission = true)
        else permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    LaunchedEffect(gpsState.hasPermission) {
        if (!gpsState.hasPermission) return@LaunchedEffect
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                gpsState = gpsState.copy(
                    latitude = it.latitude, longitude = it.longitude, isLoading = false
                )
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5 * 60 * 1000L
        ).setMinUpdateIntervalMillis(60 * 1000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                gpsState = gpsState.copy(
                    latitude = loc.latitude, longitude = loc.longitude, isLoading = false
                )
            }
        }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }
    return gpsState
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 1 — 사진 3번 스타일 사용자 목록
// ══════════════════════════════════════════════════════════════════════════════
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
                        Text("📍", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GPS 위치 확인",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        containerColor = Color.White   // 사진 3번 연한 파란 배경
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 상단 안내 배너 (사진 3번의 노란 배너) ────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFF9E0)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📡", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "실시간 위치를 확인하세요",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB8860B)
                    )
                }
            }

            // ── 사용자 카드 목록 ──────────────────────────────────────
            users.forEach { user ->
                UserLocationCard(user = user, onClick = { onUserClick(user) })
            }
        }
    }
}

// ── 사용자 카드 (사진 3번 스타일) ────────────────────────────────────────────
@Composable
private fun UserLocationCard(user: LinkedUser, onClick: () -> Unit) {
    val isWarning = user.status == UserStatus.WARNING
    val borderColor = if (isWarning) Color(0xFFFFB3B3) else Color(0xFFB3D4F5)
    val bgColor     = if (isWarning) Color(0xFFFFF5F5) else Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 아바타
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = if (isWarning) Color(0xFFFFE0E0) else Color(0xFFDCEEFC)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("👴", fontSize = 26.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 이름 + 주소 + 업데이트 시간
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${user.name}  (${user.relation})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = user.address,
                        fontSize = 13.sp,
                        color = AppColor.textTertiary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕐", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${user.lastUpdatedMin}분 전 업데이트",
                        fontSize = 12.sp,
                        color = AppColor.textTertiary
                    )
                }
            }

            // 상태 뱃지 + 화살표
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isWarning) Color(0xFFFFB3B3) else Color(0xFFB3D4F5)
                ) {
                    Text(
                        text = if (isWarning) "주의" else "양호",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWarning) Color(0xFFD9534F) else Color(0xFF1A6FAA),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = AppColor.textTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 2 — 지도 크게 + 버튼 (지도 높이 키움)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    user: LinkedUser,
    onBack: () -> Unit,
    onRouteClick: () -> Unit
) {
    val context = LocalContext.current
    var showCallDialog by remember { mutableStateOf(false) }
    val gpsState = rememberGpsState()

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
                    title = { Text("위치 정보", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

                // GPS 상태 바 (권한 오류 메시지 미표시)
                when {
                    gpsState.isLoading -> LoadingBar()
                    else -> StatusBar("현재 위치 표시 중 · 5분마다 업데이트", isError = false)
                }

                val displayLat = if (gpsState.isLoading) user.latitude else gpsState.latitude
                val displayLng = if (gpsState.isLoading) user.longitude else gpsState.longitude

                // ── 지도 영역 (크게) ──────────────────────────────────
                HighlightLocationMap(
                    latitude    = displayLat,
                    longitude   = displayLng,
                    routePoints = user.routePoints,
                    modifier    = Modifier
                        .fillMaxWidth()
                        .weight(1f)           // ← 남은 공간 전체 사용
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                )

                // ── 버튼 2개 ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                            .clickable { onRouteClick() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${user.name}님 이동 경로", fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                            .clickable { showCallDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${user.name}님 전화 걸기", fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                        }
                    }
                }
            }
        }
        if (showCallDialog) {
            CallDialog(user = user, onDismiss = { showCallDialog = false })
        }
    }
}

@Composable
private fun StatusBar(text: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isError) Color(0xFFFCEEED) else Color(0xFFF0F7EC)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null,
                tint = if (isError) Color(0xFFD9534F) else Green500,
                modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, fontSize = 12.sp,
                color = if (isError) Color(0xFFD9534F) else Green600)
        }
    }
}

@Composable
private fun LoadingBar() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp), color = Color(0xFFF0F7EC)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp),
                color = Green500, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("GPS 위치 불러오는 중...", fontSize = 12.sp, color = AppColor.textTertiary)
        }
    }
}

@Composable
private fun HighlightLocationMap(
    latitude: Double, longitude: Double,
    routePoints: List<GeoPoint>, modifier: Modifier = Modifier
) {
    val currentPoint = GeoPoint(latitude, longitude)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isClickable = true
                minZoomLevel = 4.0; maxZoomLevel = 20.0
                controller.setZoom(17.0)
                controller.setCenter(currentPoint)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            if (routePoints.size >= 2) {
                mapView.overlays.add(Polyline().apply {
                    setPoints(routePoints)
                    outlinePaint.color = 0xFF1689D9.toInt()
                    outlinePaint.strokeWidth = 13f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                })
            }
            mapView.overlays.add(Polygon().apply {
                points = Polygon.pointsAsCircle(currentPoint, 85.0)
                fillColor = 0x334CAF50; strokeColor = 0xFF2FA84F.toInt(); strokeWidth = 4f
            })
            mapView.overlays.add(Polygon().apply {
                points = Polygon.pointsAsCircle(currentPoint, 9.0)
                fillColor = 0xFFFFFFFF.toInt(); strokeColor = 0xFFFFFFFF.toInt(); strokeWidth = 2f
            })
            mapView.overlays.add(Polygon().apply {
                points = Polygon.pointsAsCircle(currentPoint, 6.0)
                fillColor = 0xFF2FA84F.toInt(); strokeColor = 0xFF1E7F3A.toInt(); strokeWidth = 2f
            })
            mapView.controller.setZoom(17.0)
            mapView.controller.animateTo(currentPoint)
            mapView.invalidate()
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 3 — 이동 경로 + 타임라인 (지도 없음 + 전체 스크롤)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationRouteScreen(user: LinkedUser, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("위치 정보", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 이동 요약 카드 3개 ────────────────────────────────────
            SummaryRow("총 경로",   "${user.totalDistanceKm} km")
            SummaryRow("방문 장소", "${user.visitedPlaces} 곳")
            SummaryRow("이동 시간", "${user.travelHours} 시간")

            Spacer(modifier = Modifier.height(4.dp))

            // ── 타임라인 ─────────────────────────────────────────────
            Text("타임 라인", fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = AppColor.textPrimary)

            Column {
                user.timeline.forEachIndexed { idx, item ->
                    TimelineRow(item = item, isLast = idx == user.timeline.size - 1)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F7EC)
            ) {
                Text("📍 위치는 5분마다 자동으로 업데이트돼요",
                    fontSize = 13.sp, color = Green600,
                    modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0F7EC),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Surface(modifier = Modifier.size(18.dp), shape = CircleShape,
                color = if (item.isCurrent) Green500 else Color(0xFFB0CCA0)) {}
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(52.dp).background(Color(0xFFB0CCA0)))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Row {
                Text(item.time, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, color = AppColor.textPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(item.label, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (item.isCurrent) Green500 else AppColor.textPrimary)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(item.address, fontSize = 13.sp, color = AppColor.textTertiary)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 4 — 전화 걸기 팝업
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun CallDialog(user: LinkedUser, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) context.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:${user.phone.replace("-", "")}"))
        )
    }

    fun makeCall() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:${user.phone.replace("-", "")}"))
            )
        } else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { onDismiss() })

        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.width(40.dp).height(4.dp)
                    .background(Color(0xFFDDDDDD), RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.height(20.dp))
                Text("${user.name}님께 전화 걸기", fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                Spacer(modifier = Modifier.height(20.dp))
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F7EC),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${user.name}님", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(user.phone, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = AppColor.textPrimary, letterSpacing = 1.sp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(modifier = Modifier.weight(1f).height(54.dp).clickable { makeCall() },
                        shape = RoundedCornerShape(14.dp), color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("전화 연결", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                        }
                    }
                    Surface(modifier = Modifier.weight(1f).height(54.dp).clickable { onDismiss() },
                        shape = RoundedCornerShape(14.dp), color = Color(0xFFF0F7EC),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("취소", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = AppColor.textTertiary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 상태 sealed class
// ══════════════════════════════════════════════════════════════════════════════
sealed class LocationScreenState {
    object List : LocationScreenState()
    data class Detail(val user: LinkedUser) : LocationScreenState()
    data class Route(val user: LinkedUser) : LocationScreenState()
}