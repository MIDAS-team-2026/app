package com.midas26.mobileapp.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.core.content.ContextCompat
import com.midas26.mobileapp.ui.theme.*

// ── 더미 사용자 데이터 모델 ──────────────────────────────────────────────────
data class LinkedUser(
    val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val lastUpdatedMin: Int,
    val totalDistanceKm: Double,
    val visitedPlaces: Int,
    val travelHours: Int,
    val timeline: List<TimelineItem>
)

data class TimelineItem(
    val time: String,
    val label: String,
    val address: String,
    val isCurrent: Boolean = false
)

// ── 더미 데이터 ───────────────────────────────────────────────────────────────
val sampleUsers = listOf(
    LinkedUser(
        id = "1", name = "홍길동",
        address = "서울시 종로구 삼봉동 123", phone = "010-1234-5678",
        lastUpdatedMin = 3, totalDistanceKm = 2.4, visitedPlaces = 3, travelHours = 4,
        timeline = listOf(
            TimelineItem("14:32", "현 위치", "서울시 종로구 삼봉동 123", isCurrent = true),
            TimelineItem("12:10", "병원",    "서울시 종로구 율곡로 456"),
            TimelineItem("10:05", "공원",    "서울시 종로구 창경궁로 789"),
        )
    ),
    LinkedUser(
        id = "2", name = "김순자",
        address = "서울시 서대문구 연희동 456", phone = "010-9876-5432",
        lastUpdatedMin = 7, totalDistanceKm = 1.1, visitedPlaces = 2, travelHours = 2,
        timeline = listOf(
            TimelineItem("13:45", "현 위치", "서울시 서대문구 연희동 456", isCurrent = true),
            TimelineItem("11:20", "약국",    "서울시 서대문구 홍제동 789"),
        )
    )
)

// ══════════════════════════════════════════════════════════════════════════════
// 화면 1 — 위치 정보 목록
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
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(color = Color(0xFFFFF9E6), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // TODO: 실제 캐릭터 이미지로 교체
                // Image(painter = painterResource(R.drawable.character_tobagi), ...)
                Text("⭐", fontSize = 72.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                users.forEach { user ->
                    UserLocationCard(user = user, onClick = { onUserClick(user) })
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
        VerticalScrollbar(state = scrollState, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun UserLocationCard(user: LinkedUser, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F7EC),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${user.name}님 위치 정보", fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(user.address, fontSize = 14.sp,
                color = AppColor.textTertiary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${user.lastUpdatedMin}분 전 업데이트",
                fontSize = 13.sp, color = AppColor.textTertiary)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 2 — 위치 상세 (지도 + 버튼)
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    user: LinkedUser,
    onBack: () -> Unit,
    onRouteClick: () -> Unit
) {
    var showCallDialog by remember { mutableStateOf(false) }

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
                    // TODO: OSMDroid 또는 Kakao Maps 연동 후 교체
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = Green500, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xCC000000)) {
                            Text(user.address, fontSize = 12.sp, color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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

                    // 전화 걸기 버튼 → showCallDialog = true
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

        // 팝업 오버레이
        if (showCallDialog) {
            CallDialog(
                user      = user,
                onDismiss = { showCallDialog = false }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 화면 3 — 이동 경로 + 타임라인
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
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryRow("총 경로",   "${user.totalDistanceKm} km")
                SummaryRow("방문 장소", "${user.visitedPlaces} 곳")
                SummaryRow("이동 시간", "${user.travelHours} 시간")
                Spacer(modifier = Modifier.height(8.dp))
                Text("타임 라인", fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                Spacer(modifier = Modifier.height(4.dp))
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
                Spacer(modifier = Modifier.height(16.dp))
            }
            VerticalScrollbar(state = scrollState, modifier = Modifier.align(Alignment.TopEnd))
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
            Surface(
                modifier = Modifier.size(18.dp), shape = CircleShape,
                color = if (item.isCurrent) Green500 else Color(0xFFB0CCA0)
            ) {}
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(52.dp)
                    .background(Color(0xFFB0CCA0)))
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
// 화면 4 — 전화 걸기 팝업 (런타임 권한 요청 포함)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun CallDialog(
    user: LinkedUser,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // ── 런타임 권한 요청 런처 ──────────────────────────────────────────────────
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 권한 허용 → 전화 걸기
            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.parse("tel:${user.phone.replace("-", "")}")
            )
            context.startActivity(intent)
        }
        // 권한 거부 시 아무것도 하지 않음 (팝업 유지)
    }

    // ── 전화 걸기 헬퍼 함수 ───────────────────────────────────────────────────
    fun makeCall() {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 이미 권한 있음 → 바로 전화 걸기
            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.parse("tel:${user.phone.replace("-", "")}")
            )
            context.startActivity(intent)
        } else {
            // 권한 없음 → 런타임 권한 요청
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 반투명 딤 배경 — 탭하면 팝업 닫힘
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )

        // 팝업 카드 (하단 고정)
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

                Text("${user.name}님께 전화 걸기",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary)

                Spacer(modifier = Modifier.height(20.dp))

                // 이름 + 전화번호 카드
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F7EC),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${user.name}님", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(user.phone, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = AppColor.textPrimary, letterSpacing = 1.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 전화 연결 버튼 — 권한 확인 후 전화 걸기
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
                            Text("전화 연결", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
                        }
                    }

                    // 취소 버튼 — 팝업 닫힘
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