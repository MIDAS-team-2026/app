package com.midas26.mobileapp.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.midas26.mobileapp.ui.theme.*

// ── 더미 사용자 데이터 모델 ──────────────────────────────────────────────────
data class LinkedUser(
    val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val lastUpdatedMin: Int,   // N분 전 업데이트
    val totalDistanceKm: Double,
    val visitedPlaces: Int,
    val travelHours: Int,
    val timeline: List<TimelineItem>
)

data class TimelineItem(
    val time: String,
    val label: String,    // "현 위치" | "병원" | "공원" 등
    val address: String,
    val isCurrent: Boolean = false
)

// 더미 데이터
val sampleUsers = listOf(
    LinkedUser(
        id = "1",
        name = "홍길동",
        address = "서울시 종로구 삼봉동 123",
        phone = "010-1234-5678",
        lastUpdatedMin = 3,
        totalDistanceKm = 2.4,
        visitedPlaces = 3,
        travelHours = 4,
        timeline = listOf(
            TimelineItem("14:32", "현 위치", "서울시 종로구 삼봉동 123", isCurrent = true),
            TimelineItem("12:10", "병원",   "서울시 종로구 율곡로 456"),
            TimelineItem("10:05", "공원",   "서울시 종로구 창경궁로 789"),
        )
    ),
    LinkedUser(
        id = "2",
        name = "김순자",
        address = "서울시 서대문구 연희동 456",
        phone = "010-9876-5432",
        lastUpdatedMin = 7,
        totalDistanceKm = 1.1,
        visitedPlaces = 2,
        travelHours = 2,
        timeline = listOf(
            TimelineItem("13:45", "현 위치", "서울시 서대문구 연희동 456", isCurrent = true),
            TimelineItem("11:20", "약국",   "서울시 서대문구 홍제동 789"),
        )
    )
)

// — 위치 정보 목록
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
                    Text(
                        "위치 정보",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── 캐릭터 이미지 자리 (또바기) ──────────────────────────────
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        color = Color(0xFFFFF9E6),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // TODO: 실제 캐릭터 이미지로 교체
                // Image(painter = painterResource(R.drawable.character_tobagi), ...)
                Text("⭐", fontSize = 72.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── 사용자 위치 카드 목록 ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                users.forEach { user ->
                    UserLocationCard(
                        user = user,
                        onClick = { onUserClick(user) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// 사용자 위치 카드
@Composable
private fun UserLocationCard(user: LinkedUser, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F7EC),       // 연한 그린 배경
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 1.5.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${user.name}님 위치 정보",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = user.address,
                fontSize = 14.sp,
                color = AppColor.textTertiary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${user.lastUpdatedMin}분 전 업데이트",
                fontSize = 13.sp,
                color = AppColor.textTertiary
            )
        }
    }
}


// 위치 상세
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    user: LinkedUser,
    onBack: () -> Unit,
    onRouteClick: () -> Unit,
    onCallClick: () -> Unit
) {
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
        ) {
            // 지도 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFD9E8D0)),
                contentAlignment = Alignment.Center
            ) {
                // TODO: Google Maps Composable로 교체
                // GoogleMap(modifier = Modifier.fillMaxSize()) { Marker(...) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Green500,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC000000)
                    ) {
                        Text(
                            text = user.address,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 버튼 2개
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 이동 경로 버튼
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

                // 전화 걸기 버튼
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clickable { onCallClick() },
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
}


// 이동 경로 및 타임라인
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationRouteScreen(
    user: LinkedUser,
    onBack: () -> Unit
) {
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 이동 요약 카드
            SummaryRow(
                label = "총 경로",
                value = "${user.totalDistanceKm} km"
            )
            SummaryRow(
                label = "방문 장소",
                value = "${user.visitedPlaces} 곳"
            )
            SummaryRow(
                label = "이동 시간",
                value = "${user.travelHours} 시간"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 타임라인
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

            // 업데이트 안내
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0F7EC)
            ) {
                Text(
                    text = "📍 위치는 5분마다 자동으로 업데이트돼요",
                    fontSize = 13.sp,
                    color = Green600,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// 요약 행
@Composable
private fun SummaryRow(label: String, value: String) {
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

// 타임라인 행
@Composable
private fun TimelineRow(item: TimelineItem, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 좌측: 점 + 세로선
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            // 점
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = if (item.isCurrent) Green500 else Color(0xFFB0CCA0)
            ) {}

            // 세로선(마지막 항목 제외)
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

        // 우측: 시간 + 라벨 + 주소
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 8.dp)) {
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
                    color = if (item.isCurrent) Green500 else AppColor.textPrimary
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


// 전화 걸기 팝업
@Composable
fun CallDialog(
    user: LinkedUser,
    backgroundContent: @Composable () -> Unit,   // 뒤에 보이는 화면 2
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // 뒤 화면
        backgroundContent()

        // 반투명 딤 처리
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() }
        )

        // 팝업 카드
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 드래그 핸들
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color(0xFFDDDDDD), RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 제목
                Text(
                    text = "${user.name}님께 전화 걸기",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 이름 + 전화번호 카드
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

                // 버튼 행
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 전화 연결 버튼
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable {
                                // 실제 전화 걸기
                                val intent = Intent(
                                    Intent.ACTION_CALL,
                                    Uri.parse("tel:${user.phone.replace("-", "")}")
                                )
                                context.startActivity(intent)
                            },
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

                    // 취소 버튼
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

// ══════════════════════════════════════════════════════════════════════════════
// 위치 정보 전체 플로우 컨트롤러
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LocationScreen(onBack: () -> Unit) {

    // 현재 서브 화면 상태
    var screen by remember { mutableStateOf<LocationScreenState>(LocationScreenState.List) }

    when (val s = screen) {

        // 화면 1: 사용자 목록
        is LocationScreenState.List -> {
            LocationListScreen(
                users = sampleUsers,
                onBack = onBack,
                onUserClick = { user ->
                    screen = LocationScreenState.Detail(user)
                }
            )
        }

        // 화면 2: 지도 + 버튼
        is LocationScreenState.Detail -> {
            var showCallDialog by remember { mutableStateOf(false) }

            if (showCallDialog) {
                // 화면 4: 전화 걸기 팝업 (화면 2 위에 오버레이)
                CallDialog(
                    user = s.user,
                    backgroundContent = {
                        LocationDetailScreen(
                            user = s.user,
                            onBack = { screen = LocationScreenState.List },
                            onRouteClick = { screen = LocationScreenState.Route(s.user) },
                            onCallClick = {}
                        )
                    },
                    onDismiss = { showCallDialog = false }
                )
            } else {
                LocationDetailScreen(
                    user = s.user,
                    onBack = { screen = LocationScreenState.List },
                    onRouteClick = { screen = LocationScreenState.Route(s.user) },
                    onCallClick  = { showCallDialog = true }
                )
            }
        }

        // 화면 3: 이동 경로 + 타임라인
        is LocationScreenState.Route -> {
            LocationRouteScreen(
                user = s.user,
                onBack = { screen = LocationScreenState.Detail(s.user) }
            )
        }
    }
}

// ── 화면 상태 sealed class ────────────────────────────────────────────────────
sealed class LocationScreenState {
    object List : LocationScreenState()
    data class Detail(val user: LinkedUser) : LocationScreenState()
    data class Route(val user: LinkedUser) : LocationScreenState()
}