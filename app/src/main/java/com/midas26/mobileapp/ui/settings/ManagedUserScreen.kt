package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.ui.guardian.GuardianViewModel
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun ManagedUserScreen(
    patients: List<LinkedUserInfo>,
    isLoading: Boolean = false,
    viewModel: GuardianViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = PrefsManager.from(context)

    // 관계를 즉시 반영하기 위해 로컬 상태로 관리
    val relationMap = remember(patients) {
        mutableStateMapOf<Int, String>().also { map ->
            patients.forEach { p ->
                p.userId?.let { id -> map[id] = prefs.getPatientRelation(id) }
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showUnlinkDialog by remember { mutableStateOf<LinkedUserInfo?>(null) }
    var showDetailDialog by remember { mutableStateOf<LinkedUserInfo?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }

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
                        val relation = patient.userId?.let { relationMap[it] } ?: ""
                        ManagedUserItem(
                            patient = patient,
                            relation = relation,
                            onUnlink = { showUnlinkDialog = patient },
                            onClick = { showDetailDialog = patient }
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

    // 상세 정보 다이얼로그
    showDetailDialog?.let { patient ->
        PatientDetailDialog(
            patient = patient,
            initialRelation = patient.userId?.let { relationMap[it] } ?: "",
            onDismiss = { showDetailDialog = null },
            onSave = { newRelation ->
                patient.userId?.let { id ->
                    prefs.savePatientRelation(id, newRelation.trim())
                    relationMap[id] = newRelation.trim()
                }
                showDetailDialog = null
            }
        )
    }

    // 사용자 추가 다이얼로그
    if (showAddDialog) {
        AddPatientDialog(
            errorMessage = addError,
            onDismiss = { showAddDialog = false; addError = null },
            onConfirm = { code, name, phone, relation ->
                addError = null
                viewModel.verifyAndLinkPatient(
                    context   = context,
                    patientCode = code,
                    inputName   = name,
                    inputPhone  = phone,
                    relation    = relation,
                    onSuccess = { showAddDialog = false; addError = null },
                    onError   = { msg -> addError = msg }
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
                        viewModel.unlinkPatientWithRelation(
                            context   = context,
                            patientId = id,
                            onSuccess = { showUnlinkDialog = null },
                            onError   = { showUnlinkDialog = null }
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
    relation: String,
    onUnlink: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        shadowElevation = AppColor.cardShadowElevation,
        border = AppColor.cardBorder,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = patient.name ?: "이름 없음",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColor.textPrimary
                    )
                    if (relation.isNotEmpty()) {
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
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = patient.phone ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = AppColor.errorSurface,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                TextButton(onClick = onUnlink) {
                    Text(
                        text = "연결 해제",
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
private fun PatientDetailDialog(
    patient: LinkedUserInfo,
    initialRelation: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var relation by remember { mutableStateOf(initialRelation) }

    val disabledColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = AppColor.textPrimary,
        disabledBorderColor = AppColor.divider,
        disabledLabelColor = AppColor.textTertiary,
        disabledContainerColor = Color.Transparent
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("사용자 상세 정보", fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = patient.name ?: "",
                    onValueChange = {},
                    label = { Text("이름") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = disabledColors
                )
                OutlinedTextField(
                    value = patient.phone ?: "",
                    onValueChange = {},
                    label = { Text("전화번호") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = disabledColors
                )
                OutlinedTextField(
                    value = patient.patientCode ?: "",
                    onValueChange = {},
                    label = { Text("사용자 코드") },
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = disabledColors
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("관계 (수정 가능)") },
                    placeholder = { Text("예: 부, 모, 조부") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(relation) }) {
                Text("저장", color = AppColor.guardianPrimary, fontWeight = FontWeight.Bold)
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
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (code: String, name: String, phone: String, relation: String) -> Unit
) {
    var code     by remember { mutableStateOf("") }
    var name     by remember { mutableStateOf("") }
    var phone    by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }

    val canSubmit = code.length == 8 && name.isNotBlank() && phone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("사용자 추가", fontWeight = FontWeight.Bold, color = AppColor.textPrimary)
        },
        text = {
            Column {
                Text(
                    text = "사용자 코드와 정보를 입력해 본인 확인 후 연동합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                    label = { Text("사용자 코드 (8자리)") },
                    placeholder = { Text("예: A1B2C3D4") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("전화번호") },
                    placeholder = { Text("010-0000-0000") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("관계 (선택)") },
                    placeholder = { Text("예: 부, 모, 조부") },
                    singleLine = true
                )

                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.errorPrimary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onConfirm(code, name, phone, relation) },
                enabled = canSubmit
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
