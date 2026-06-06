package com.midas26.mobileapp.ui.analysis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.guardian.PatientAnalysisStatus
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.util.PrefsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisUserSelectScreen(
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
    patientStatuses: Map<Int, PatientAnalysisStatus> = emptyMap(),
    onBack: () -> Unit,
    onUserClick: (LinkedUserInfo) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📊", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "분석 결과 확인",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                patients.isEmpty() -> {
                    Text(
                        text = "연결된 사용자가 없습니다",
                        color = AppColor.textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF0F4FF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📋", fontSize = 26.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "분석 결과를 확인할 사용자를 선택하세요",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A4FAA)
                                )
                            }
                        }

                        patients.forEach { patient ->
                            val status = patient.userId?.let { patientStatuses[it] }
                            AnalysisUserCard(
                                patient = patient,
                                status = status,
                                onClick = { onUserClick(patient) }
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
    }
}

@Composable
private fun AnalysisUserCard(
    patient: LinkedUserInfo,
    status: PatientAnalysisStatus?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val relation = patient.userId
        ?.let { PrefsManager.from(context).getPatientRelation(it) }
        ?.ifEmpty { "사용자" }
        ?: "사용자"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(100.dp)
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
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 이름 + 관계 pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = patient.name ?: "이름 없음",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColor.guardianSurface
                    ) {
                        Text(
                            text = relation,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColor.guardianDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // 상태 문구
                when (status) {
                    PatientAnalysisStatus.LOADING -> StatusShimmer()
                    PatientAnalysisStatus.NEW_RESULT -> Text(
                        text = "새로운 분석 결과가 나왔어요!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF59E0B)
                    )
                    PatientAnalysisStatus.VIEWED_TODAY -> Text(
                        text = "오늘의 분석 결과를 확인했어요!",
                        fontSize = 13.sp,
                        color = AppColor.textTertiary
                    )
                    PatientAnalysisStatus.NO_RESULT, null -> Text(
                        text = "아직 오늘의 분석 결과가 도착하지 않았어요",
                        fontSize = 13.sp,
                        color = AppColor.textTertiary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColor.textTertiary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** 상태 로딩 중 shimmer 효과 */
@Composable
private fun StatusShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(13.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE0E0E0),
                        Color(0xFFF5F5F5),
                        Color(0xFFE0E0E0)
                    ),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + 200f, 0f)
                ),
                shape = RoundedCornerShape(6.dp)
            )
    )
}
