package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.guardian.GuardianViewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite

@Composable
fun ManagedUserScreen(
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
    viewModel: GuardianViewModel,
    onBack: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showUnlinkDialog by remember { mutableStateOf<LinkedUserInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColor.surfaceElevated)
    ) {
        ManagedUserTopBar(title = "관리 중인 사용자", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = AppColor.guardianPrimary
                    )
                }
                patients.isEmpty() -> {
                    Text(
                        text = "연결된 사용자가 없습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColor.textTertiary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    patients.forEach { patient ->
                        ManagedUserItem(
                            patient = patient,
                            onUnlink = { showUnlinkDialog = patient }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AddUserButton(onClick = { showAddDialog = true })

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 사용자 추가 다이얼로그
    if (showAddDialog) {
        AddPatientDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { code ->
                viewModel.linkPatient(
                    patientCode = code,
                    onSuccess = { showAddDialog = false },
                    onError = { /* TODO: 토스트 등 에러 표시 */ }
                )
            }
        )
    }

    // 연동 해제 확인 다이얼로그
    showUnlinkDialog?.let { patient ->
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = null },
            title = {
                Text("연동 해제", fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
            },
            text = {
                Text(
                    "${patient.name ?: "이 사용자"}와의 연동을 해제할까요?",
                    color = AppColor.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    patient.userId?.let { id ->
                        viewModel.unlinkPatient(
                            patientId = id,
                            onSuccess = { showUnlinkDialog = null },
                            onError = { showUnlinkDialog = null }
                        )
                    }
                }) {
                    Text("해제", color = AppColor.errorPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = null }) {
                    Text("취소", color = AppColor.textTertiary)
                }
            },
            containerColor = BrandWhite,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ManagedUserTopBar(title: String, onBack: () -> Unit) {
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
    patient: LinkedUserInfo,
    onUnlink: () -> Unit
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
                    text = patient.name ?: "이름 없음",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = patient.phone ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textSecondary
                )
                if (!patient.patientCode.isNullOrEmpty()) {
                    Text(
                        text = "코드: ${patient.patientCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColor.textTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = AppColor.errorSurface,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                TextButton(onClick = onUnlink) {
                    Text(
                        text = "해제",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.errorPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AddUserButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder,
        onClick = onClick
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
                color = AppColor.guardianPrimary
            )
        }
    }
}

@Composable
private fun AddPatientDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("사용자 추가", fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
        },
        text = {
            Column {
                Text(
                    text = "사용자의 8자리 코드를 입력하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        code = input.uppercase()
                            .filter { it.isLetterOrDigit() }
                            .take(8)
                    },
                    label = { Text("사용자 코드") },
                    singleLine = true,
                    placeholder = { Text("예: A1B2C3D4") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (code.length == 8) onConfirm(code) },
                enabled = code.length == 8
            ) {
                Text("추가", color = AppColor.guardianPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = AppColor.textTertiary)
            }
        },
        containerColor = BrandWhite,
        shape = RoundedCornerShape(20.dp)
    )
}
