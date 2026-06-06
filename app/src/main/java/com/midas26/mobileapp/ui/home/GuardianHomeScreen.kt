package com.midas26.mobileapp.ui.home

import com.midas26.mobileapp.ui.theme.AppColor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.theme.*

enum class GuardianMenu {
    Analysis,
    Location,
    Settings
}

@Composable
fun GuardianHomeScreen(
    guardianName: String,
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
    onMenuClick: (GuardianMenu) -> Unit = {}
) {
    var selectedPatient by remember(patients) {
        mutableStateOf(patients.firstOrNull())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GuardianHomeHeader(
            guardianName = guardianName,
            patients = patients,
            selectedPatient = selectedPatient,
            isLoading = isLoading,
            onPatientSelected = { selectedPatient = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuList(
            onMenuClick = onMenuClick,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun GuardianHomeHeader(
    guardianName: String,
    patients: List<LinkedUserInfo>,
    selectedPatient: LinkedUserInfo?,
    isLoading: Boolean,
    onPatientSelected: (LinkedUserInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AppColor.guardianDark,
                        AppColor.guardianPrimary
                    )
                )
            )
    ) {
        // 배경 원 장식
        Box(
            modifier = Modifier
                .size(190.dp)
                .offset(x = 235.dp, y = 22.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "오늘의 점수",
                    fontSize = 15.sp,
                    color = BrandWhite.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "-",
                    fontSize = 52.sp,
                    color = BrandWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = (-30).dp, y = 110.dp)
                .clip(CircleShape)
                .background(BrandWhite.copy(alpha = 0.10f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp)
        ) {
            Text(
                text = "$guardianName ${stringResource(R.string.guardian_user_suffix)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )

            Spacer(modifier = Modifier.height(18.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = BrandWhite,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                patients.isEmpty() -> {
                    Text(
                        text = "연결된 사용자가 없습니다",
                        fontSize = 16.sp,
                        color = BrandWhite.copy(alpha = 0.8f)
                    )
                }
                else -> {
                    LinkedPatientDropdown(
                        patients = patients,
                        selectedPatient = selectedPatient,
                        onPatientSelected = onPatientSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedPatientDropdown(
    patients: List<LinkedUserInfo>,
    selectedPatient: LinkedUserInfo?,
    onPatientSelected: (LinkedUserInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier.clickable { expanded = !expanded },
            shape = RoundedCornerShape(24.dp),
            color = BrandWhite.copy(alpha = 0.22f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = AppColor.guardianDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${selectedPatient?.name ?: "사용자 선택"} 님 연결됨",
                    fontSize = 18.sp,
                    color = BrandWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            patients.forEach { patient ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${patient.name ?: "이름 없음"} 님",
                            fontWeight = if (patient.userId == selectedPatient?.userId)
                                FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onPatientSelected(patient)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GuardianMenuList(
    onMenuClick: (GuardianMenu) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.Top
    ) {
        SectionHeader(title = stringResource(R.string.guardian_menu))

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuCard(
            icon = Icons.Default.BarChart,
            title = stringResource(R.string.guardian_menu_analysis),
            desc = "대화 결과와 인지 점수를 확인해요",
            onClick = { onMenuClick(GuardianMenu.Analysis) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuCard(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.guardian_menu_location),
            desc = "보호 대상자의 현재 위치를 확인해요",
            onClick = { onMenuClick(GuardianMenu.Location) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        SectionHeader(title = stringResource(R.string.section_settings))

        Spacer(modifier = Modifier.height(12.dp))

        GuardianMenuCard(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.menu_settings),
            desc = "앱 환경과 알림을 설정해요",
            onClick = { onMenuClick(GuardianMenu.Settings) },
            accent = AppColor.surfaceElevated,
            iconTint = AppColor.textTertiary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppColor.divider)
        )
    }
}

@Composable
private fun GuardianMenuCard(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified
) {
    val resolvedAccent   = if (accent   == Color.Unspecified) AppColor.guardianSurface else accent
    val resolvedIconTint = if (iconTint == Color.Unspecified) AppColor.guardianDark    else iconTint

    Surface(
        modifier = modifier
            .height(104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }

            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(20.dp),
                color = resolvedAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedIconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}
