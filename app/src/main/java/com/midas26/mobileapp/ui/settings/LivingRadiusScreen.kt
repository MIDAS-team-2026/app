package com.midas26.mobileapp.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.network.LocationRepository
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Green50
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign

private data class RadiusUser(
    val id: String,
    val numericUserId: Int,
    val name: String,
    val relation: String,
    val phone: String
)

private data class RadiusAddress(
    val id: String,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

private enum class LivingRadiusOption(
    val label: String,
    val descriptionLabel: String,
    val radiusMeter: Double
) {
    M500("500m", "500m", 500.0),
    KM1_5("1.5km", "1.5km", 1500.0),
    KM3("3km", "3km", 3000.0)
}

@Composable
fun LivingRadiusScreen(
    onBack: () -> Unit = {}
) {
    val users = remember {
        listOf(
            RadiusUser(
                id = "A1B2C3D4",
                numericUserId = 1,
                name = "홍길동",
                relation = "부",
                phone = "010-1234-5678"
            ),
            RadiusUser(
                id = "E5F6A7B8",
                numericUserId = 2,
                name = "김영희",
                relation = "모",
                phone = "010-9876-5432"
            )
        )
    }

    var selectedUser by remember { mutableStateOf<RadiusUser?>(null) }

    if (selectedUser != null) {
        UserLivingRadiusScreen(
            user = selectedUser!!,
            onBack = { selectedUser = null }
        )
    } else {
        RadiusUserSelectScreen(
            users = users,
            onBack = onBack,
            onUserClick = { user ->
                selectedUser = user
            }
        )
    }
}

@Composable
private fun RadiusUserSelectScreen(
    users: List<RadiusUser>,
    onBack: () -> Unit,
    onUserClick: (RadiusUser) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        RadiusTopBar(
            title = "생활 반경 설정",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            users.forEachIndexed { index, user ->
                RadiusUserRow(
                    name = "${user.name} (${user.relation})",
                    subText = user.phone,
                    onClick = { onUserClick(user) }
                )

                if (index != users.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun UserLivingRadiusScreen(
    user: RadiusUser,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val addresses = remember(user) {
        listOf(
            RadiusAddress(
                id = "home",
                label = "집",
                address = "서울특별시 종로구 종로 1가",
                latitude = 37.5700,
                longitude = 126.9820
            ),
            RadiusAddress(
                id = "hospital",
                label = "병원",
                address = "서울특별시 종로구 대학로 101",
                latitude = 37.5796,
                longitude = 126.9996
            )
        )
    }

    val selectedRadiusMap = remember(user) {
        mutableStateMapOf<String, LivingRadiusOption>().apply {
            addresses.forEach { address ->
                this[address.id] = LivingRadiusOption.M500
            }
        }
    }

    fun saveSafeZone(
        address: RadiusAddress,
        option: LivingRadiusOption
    ) {
        scope.launch {
            LocationRepository.saveSafeZone(
                userId = user.numericUserId,
                zoneName = address.label,
                latitude = address.latitude,
                longitude = address.longitude,
                radius = option.radiusMeter
            ).onSuccess {
                Toast.makeText(
                    context,
                    "${address.label} 반경이 ${option.label}로 저장됐어요.",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    "생활 반경 저장에 실패했어요.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        RadiusTopBar(
            title = "${user.name}님 생활 반경",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            RadiusSectionCard {
                Text(
                    text = "생활 반경 이탈 시 알림을 보내요.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                addresses.forEachIndexed { index, address ->
                    AddressRadiusItem(
                        address = address,
                        selectedRadius = selectedRadiusMap[address.id] ?: LivingRadiusOption.M500,
                        onSelect = { option ->
                            selectedRadiusMap[address.id] = option
                            saveSafeZone(address, option)
                        }
                    )

                    if (index != addresses.lastIndex) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RadiusTopBar(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandWhite,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 12.dp, start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = AppColor.textPrimary
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        }
    }
}

@Composable
private fun RadiusUserRow(
    name: String,
    subText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColor.textTertiary
            )
        }
    }
}

@Composable
private fun RadiusSectionCard(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AddressRadiusItem(
    address: RadiusAddress,
    selectedRadius: LivingRadiusOption,
    onSelect: (LivingRadiusOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = address.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppColor.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = address.address,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LivingRadiusOption.values().forEach { option ->
                RadiusOptionChip(
                    label = option.label,
                    selected = selectedRadius == option,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Green50,
            border = BorderStroke(1.dp, Green50)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${address.label}에서 반경 ${selectedRadius.descriptionLabel} 이탈 시 알림",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColor.textPrimary
                )
            }
        }
    }
}

@Composable
private fun RadiusOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Green50 else BrandWhite,
        border = BorderStroke(
            width = 1.dp,
            color = Green50
        )
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = AppColor.textPrimary
            )
        }
    }
}