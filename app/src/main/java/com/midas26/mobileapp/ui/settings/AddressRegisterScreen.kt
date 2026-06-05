package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.GuardianAccentDark

private data class AddressUser(
    val name: String,
    val relation: String,
    val phone: String
)

private data class UserAddress(
    val label: String,
    val address: String,
    val jibunAddress: String = "",
    val zipCode: String = ""
)

private data class AddressSearchResult(
    val roadAddress: String,
    val jibunAddress: String,
    val zipCode: String
)

@Composable
fun AddressRegisterScreen(
    onBack: () -> Unit = {}
) {
    val users = remember {
        listOf(
            AddressUser("홍길동", "부", "010-1234-5678"),
            AddressUser("김영희", "모", "010-9876-5432")
        )
    }

    var selectedUser by remember { mutableStateOf<AddressUser?>(null) }

    if (selectedUser != null) {
        UserAddressRegisterScreen(
            user = selectedUser!!,
            onBack = { selectedUser = null }
        )
    } else {
        AddressUserSelectScreen(
            users = users,
            onBack = onBack,
            onUserClick = { selectedUser = it }
        )
    }
}

@Composable
private fun AddressUserSelectScreen(
    users: List<AddressUser>,
    onBack: () -> Unit,
    onUserClick: (AddressUser) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        AddressTopBar(
            title = "주소 등록",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            users.forEachIndexed { index, user ->
                UserSelectRow(
                    name = "${user.name} (${user.relation})",
                    subText = user.phone,
                    onClick = { onUserClick(user) }
                )

                if (index != users.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun UserAddressRegisterScreen(
    user: AddressUser,
    onBack: () -> Unit
) {
    var addresses by remember(user) {
        mutableStateOf(
            listOf(
                UserAddress(
                    label = "집",
                    address = "서울특별시 종로구 종로 1가",
                    jibunAddress = "서울특별시 종로구 종로1가",
                    zipCode = "03154"
                ),
                UserAddress(
                    label = "병원",
                    address = "서울특별시 종로구 대학로 101",
                    jibunAddress = "서울특별시 종로구 연건동",
                    zipCode = "03080"
                )
            )
        )
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        AddressTopBar(
            title = "${user.name}님 주소 등록",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            AddressSectionCard {
                addresses.forEachIndexed { index, address ->
                    AddressItem(
                        address = address,
                        onEdit = {
                            editingIndex = index
                            isAdding = false
                            showEditDialog = true
                        }
                    )

                    if (index != addresses.lastIndex) {
                        HorizontalDivider(
                            color = AppColor.divider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                AddAddressButton(
                    onClick = {
                        editingIndex = null
                        isAdding = true
                        showEditDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showEditDialog) {
        val targetAddress = editingIndex?.let { addresses[it] }
            ?: UserAddress(label = "", address = "")

        AddressEditDialog(
            title = if (isAdding) "주소 추가" else "주소 수정",
            address = targetAddress,
            onDismiss = {
                showEditDialog = false
                editingIndex = null
                isAdding = false
            },
            onSave = { edited ->
                addresses = if (isAdding) {
                    addresses + edited
                } else {
                    addresses.toMutableList().also { list ->
                        editingIndex?.let { index ->
                            list[index] = edited
                        }
                    }
                }

                showEditDialog = false
                editingIndex = null
                isAdding = false
            }
        )
    }
}

@Composable
private fun AddressEditDialog(
    title: String,
    address: UserAddress,
    onDismiss: () -> Unit,
    onSave: (UserAddress) -> Unit
) {
    var label by remember { mutableStateOf(address.label) }
    var selectedAddress by remember { mutableStateOf(address) }
    var showSearchScreen by remember { mutableStateOf(false) }

    if (showSearchScreen) {
        AddressSearchScreen(
            onBack = { showSearchScreen = false },
            onSelect = { result ->
                selectedAddress = selectedAddress.copy(
                    address = result.roadAddress,
                    jibunAddress = result.jibunAddress,
                    zipCode = result.zipCode
                )
                showSearchScreen = false
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("장소 이름") },
                    placeholder = { Text("예: 집, 병원, 복지관") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSearchScreen = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Gray100,
                    border = AppColor.cardBorder
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "주소",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColor.textTertiary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = selectedAddress.address.ifBlank { "주소를 검색해주세요" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColor.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (selectedAddress.jibunAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "지번 ${selectedAddress.jibunAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.textTertiary
                    )
                }

                if (selectedAddress.zipCode.isNotBlank()) {
                    Text(
                        text = "우편번호 ${selectedAddress.zipCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.textTertiary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        UserAddress(
                            label = label.ifBlank { "주소" },
                            address = selectedAddress.address.ifBlank { "주소 미선택" },
                            jibunAddress = selectedAddress.jibunAddress,
                            zipCode = selectedAddress.zipCode
                        )
                    )
                }
            ) {
                Text(
                    text = "저장",
                    color = GuardianAccentDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "취소",
                    color = AppColor.textTertiary
                )
            }
        },
        containerColor = BrandWhite,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun AddressSearchScreen(
    onBack: () -> Unit,
    onSelect: (AddressSearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }

    val results = remember(query, searched) {
        if (!searched || query.isBlank()) {
            emptyList()
        } else {
            sampleAddressResults(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = AppColor.textPrimary
                )
            }

            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = AppColor.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = if (searched) "주소를 선택해주세요" else "주소를 검색해주세요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppColor.textPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    searched = false
                },
                placeholder = { Text("도로명, 지번, 건물명 검색") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                modifier = Modifier
                    .height(56.dp)
                    .clickable {
                        searched = true
                    },
                shape = RoundedCornerShape(8.dp),
                color = Green50
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "검색",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (!searched) {
            Text(
                text = "이렇게 검색해보세요!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianAccentDark
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "· 도로명 + 건물번호\n  예) 정자일로 95, 불정로 6\n\n· 동/읍/면/리 + 번지\n  예) 정자동 178-4, 동면 만천리 1000",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textPrimary
            )
        } else {
            results.forEach { result ->
                AddressSearchResultItem(
                    result = result,
                    onSelect = { onSelect(result) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AddressSearchResultItem(
    result: AddressSearchResult,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.roadAddress,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "지번 ${result.jibunAddress}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary
            )

            Text(
                text = "우편번호 ${result.zipCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textTertiary
            )
        }

        Surface(
            modifier = Modifier.clickable(onClick = onSelect),
            shape = RoundedCornerShape(8.dp),
            color = Green50
        ) {
            Text(
                text = "선택",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

private fun sampleAddressResults(query: String): List<AddressSearchResult> {
    val trimmed = query.trim()

    return listOf(
        AddressSearchResult(
            roadAddress = "대구광역시 수성구 천을로 2 (시지동)",
            jibunAddress = "대구광역시 수성구 시지동 169-10",
            zipCode = "42262"
        ),
        AddressSearchResult(
            roadAddress = "서울특별시 종로구 종로 1가",
            jibunAddress = "서울특별시 종로구 종로1가",
            zipCode = "03154"
        ),
        AddressSearchResult(
            roadAddress = "$trimmed 인근 주소",
            jibunAddress = "$trimmed 지번 주소",
            zipCode = "00000"
        )
    )
}

@Composable
private fun AddressTopBar(
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
private fun UserSelectRow(
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
private fun AddressSectionCard(
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
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AddressItem(
    address: UserAddress,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = address.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = address.address,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textSecondary
            )
        }

        Text(
            text = "수정",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppColor.textPrimary,
            modifier = Modifier
                .clickable(onClick = onEdit)
                .background(
                    color = Green50,
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AddAddressButton(
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Green50
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+  주소 추가",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        }
    }
}