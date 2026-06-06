package com.midas26.mobileapp.ui.home

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
import com.midas26.mobileapp.ui.theme.*

enum class GuardianMenu {
    Analysis,
    Location,
    Settings
}

data class LinkedUserDummyData(
    val userId: Int,
    val userName: String,
    val todayScore: Int?
)

data class GuardianLinkedDummyData(
    val guardianName: String,
    val linkedUsers: List<LinkedUserDummyData>
)

private val guardianLinkedDummyData = GuardianLinkedDummyData(
    guardianName = "홍철수",
    linkedUsers = listOf(
        LinkedUserDummyData(
            userId = 1,
            userName = "홍길동",
            todayScore = 75
        ),
        LinkedUserDummyData(
            userId = 2,
            userName = "김영희",
            todayScore = null
        ),
        LinkedUserDummyData(
            userId = 3,
            userName = "박민수",
            todayScore = 68
        )
    )
)

@Composable
fun GuardianHomeScreen(
    onMenuClick: (GuardianMenu) -> Unit = {}
) {
    val guardianData = guardianLinkedDummyData

    var selectedUser by remember {
        mutableStateOf(guardianData.linkedUsers.first())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GuardianHomeHeader(
            guardianName = guardianData.guardianName,
            linkedUsers = guardianData.linkedUsers,
            selectedUser = selectedUser,
            onUserSelected = { selectedUser = it }
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
    linkedUsers: List<LinkedUserDummyData>,
    selectedUser: LinkedUserDummyData,
    onUserSelected: (LinkedUserDummyData) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GuardianAccentDark,
                        GuardianAccent
                    )
                )
            )
    ) {
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

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = selectedUser.todayScore?.toString() ?: "-",
                        fontSize = 52.sp,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedUser.todayScore != null) {
                        Text(
                            text = stringResource(R.string.home_score_unit),
                            fontSize = 19.sp,
                            color = BrandWhite.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
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

            LinkedUserDropdown(
                linkedUsers = linkedUsers,
                selectedUser = selectedUser,
                onUserSelected = onUserSelected
            )
        }
    }
}

@Composable
private fun LinkedUserDropdown(
    linkedUsers: List<LinkedUserDummyData>,
    selectedUser: LinkedUserDummyData,
    onUserSelected: (LinkedUserDummyData) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box {
        Surface(
            modifier = Modifier.clickable {
                expanded = !expanded
            },
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
                    tint = GuardianAccentDark,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${selectedUser.userName} 님 연결됨",
                    fontSize = 18.sp,
                    color = BrandWhite,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
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
            linkedUsers.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${user.userName} 님",
                            fontWeight = if (user.userId == selectedUser.userId) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    onClick = {
                        onUserSelected(user)
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
            accent = Gray100,
            iconTint = Gray400,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionHeader(
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Gray400,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Gray200)
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
    accent: Color = GuardianAccentLight,
    iconTint: Color = GuardianAccentDark
) {
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
            Column(
                modifier = Modifier.weight(1f)
            ) {
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
                color = accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}