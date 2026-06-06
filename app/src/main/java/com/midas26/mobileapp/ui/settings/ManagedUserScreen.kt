package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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

private data class ManagedUser(
    val name: String,
    val relation: String,
    val phone: String,
    val birth: String,
    val userCode: String
)

@Composable
fun ManagedUserScreen(
    onBack: () -> Unit = {}
) {
    var users by remember {
        mutableStateOf(
            listOf(
                ManagedUser(
                    name = "홍길동",
                    relation = "부",
                    phone = "010-1234-5678",
                    birth = "1950.05.07",
                    userCode = "A1B2C3D4"
                ),
                ManagedUser(
                    name = "김영희",
                    relation = "모",
                    phone = "010-9876-5432",
                    birth = "1951.07.29",
                    userCode = "E5F6A7B8"
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
            .background(AppColor.surfaceElevated)
    ) {
        ManagedUserTopBar(
            title = "관리 중인 사용자",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            users.forEachIndexed { index, user ->
                ManagedUserItem(
                    user = user,
                    onEdit = {
                        editingIndex = index
                        isAdding = false
                        showEditDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            AddUserButton(
                onClick = {
                    editingIndex = null
                    isAdding = true
                    showEditDialog = true
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showEditDialog) {
        val targetUser = editingIndex?.let { users[it] }
            ?: ManagedUser(
                name = "",
                relation = "",
                phone = "",
                birth = "",
                userCode = ""
            )

        ManagedUserEditDialog(
            title = if (isAdding) "사용자 추가" else "사용자 수정",
            user = targetUser,
            isAdding = isAdding,
            onDismiss = {
                showEditDialog = false
                editingIndex = null
                isAdding = false
            },
            onSave = { edited ->
                users = if (isAdding) {
                    users + edited
                } else {
                    users.toMutableList().also { list ->
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
private fun ManagedUserTopBar(
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
private fun ManagedUserItem(
    user: ManagedUser,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${user.name} (${user.relation})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = user.phone,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textPrimary
                )

                Text(
                    text = user.birth,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textPrimary
                )

                Text(
                    text = "사용자 코드: ${user.userCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = AppColor.greenSurface,
                modifier = Modifier.clickable(onClick = onEdit)
            ) {
                Text(
                    text = "수정",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AddUserButton(
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
            modifier = Modifier.padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+  사용자 추가",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppColor.textPrimary
            )
        }
    }
}

@Composable
private fun ManagedUserEditDialog(
    title: String,
    user: ManagedUser,
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onSave: (ManagedUser) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var relation by remember { mutableStateOf(user.relation) }
    var phone by remember { mutableStateOf(user.phone) }
    var birth by remember { mutableStateOf(user.birth) }
    var userCode by remember { mutableStateOf(user.userCode) }

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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("관계") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("전화번호") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = birth,
                    onValueChange = { birth = it },
                    label = { Text("생년월일") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = userCode,
                    onValueChange = { input ->
                        val filtered = input
                            .uppercase()
                            .filter { it in '0'..'9' || it in 'A'..'F' }
                            .take(8)

                        userCode = filtered
                    },
                    label = { Text("사용자 코드") },
                    singleLine = true,
                    enabled = isAdding
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ManagedUser(
                            name = name.ifBlank { "이름 없음" },
                            relation = relation.ifBlank { "관계" },
                            phone = phone.ifBlank { "010-0000-0000" },
                            birth = birth.ifBlank { "1950.01.01" },
                            userCode = userCode.ifBlank { "00000000" }.uppercase()
                        )
                    )
                }
            ) {
                Text(
                    text = "저장",
                    color = AppColor.guardianDark,
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