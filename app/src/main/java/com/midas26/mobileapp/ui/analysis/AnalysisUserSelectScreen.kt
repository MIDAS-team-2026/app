package com.midas26.mobileapp.ui.analysis

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import com.midas26.mobileapp.ui.theme.AppColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisUserSelectScreen(
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
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
                            AnalysisUserCard(
                                patient = patient,
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
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(110.dp)
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
                    text = patient.name ?: "이름 없음",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = patient.phone ?: "",
                    fontSize = 14.sp,
                    color = AppColor.textTertiary
                )
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
