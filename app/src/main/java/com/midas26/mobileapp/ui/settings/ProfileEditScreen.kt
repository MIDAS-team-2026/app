package com.midas26.mobileapp.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.util.PrefsManager

@Composable
fun ProfileEditScreen(
    initialName: String = "",
    initialPhone: String = "",
    onBack: () -> Unit = {},
    viewModel: ProfileEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val prefs = PrefsManager.from(context)
    val isPatient = prefs.getUserRole() == PrefsManager.ROLE_USER
    val userCode = prefs.getUserCode()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var currentPasswordError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val state by viewModel.state.collectAsState()
    val isLoading = state is ProfileEditState.Loading

    LaunchedEffect(state) {
        when (state) {
            is ProfileEditState.PasswordChanged -> {
                viewModel.resetState()
                Toast.makeText(context, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                onBack()
            }
            is ProfileEditState.Error -> {
                serverError = (state as ProfileEditState.Error).message
            }
            else -> {}
        }
    }

    fun validate(): Boolean {
        var ok = true
        currentPasswordError = if (currentPassword.isEmpty()) {
            ok = false; "현재 비밀번호를 입력해주세요."
        } else null
        newPasswordError = when {
            newPassword.isEmpty() -> { ok = false; "새 비밀번호를 입력해주세요." }
            newPassword.length < 8 -> { ok = false; "8자 이상 입력해주세요." }
            else -> null
        }
        confirmPasswordError = when {
            confirmPassword.isEmpty() -> { ok = false; "비밀번호 확인을 입력해주세요." }
            confirmPassword != newPassword -> { ok = false; "비밀번호가 일치하지 않습니다." }
            else -> null
        }
        return ok
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray100)
    ) {
        // 상단 바
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BrandWhite,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 12.dp, start = 4.dp, end = 16.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = AppColor.textPrimary
                    )
                }
                Text(
                    text = "프로필",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // 아바타
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = Green50
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initialName.take(1).ifEmpty { "?" },
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColor.accentDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 사용자 코드 섹션 — 환자만 표시
            if (isPatient) {
                ProfileSection(title = "사용자 코드") {
                    val displayFirst = if (userCode.length >= 4) userCode.take(4) else "____"
                    val displaySecond = if (userCode.length >= 8) userCode.drop(4) else "____"
                    val hasCode = userCode.isNotEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$displayFirst  $displaySecond",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasCode) AppColor.textPrimary
                                        else AppColor.textTertiary,
                                letterSpacing = 3.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "보호자에게 이 코드를 알려주세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColor.textTertiary
                            )
                        }
                        if (hasCode) {
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(userCode))
                                        Toast.makeText(context, "코드가 복사되었어요", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = Gray200
                            ) {
                                Text(
                                    text = "복사",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColor.textSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 기본 정보 섹션 (수정 불가)
            ProfileSection(title = "기본 정보") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AppOutlinedTextField(
                        value = initialName,
                        onValueChange = {},
                        label = "이름",
                        leadingIcon = Icons.Filled.Person,
                        enabled = false,
                        imeAction = ImeAction.None
                    )
                    AppOutlinedTextField(
                        value = initialPhone,
                        onValueChange = {},
                        label = "전화번호",
                        leadingIcon = Icons.Filled.Phone,
                        keyboardType = KeyboardType.Phone,
                        enabled = false,
                        imeAction = ImeAction.None
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 비밀번호 변경 섹션
            ProfileSection(title = "비밀번호 변경") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AppOutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            currentPasswordError = null
                            serverError = null
                        },
                        label = "현재 비밀번호",
                        leadingIcon = Icons.Filled.Lock,
                        isPassword = true,
                        errorText = currentPasswordError,
                        imeAction = ImeAction.Next
                    )
                    HorizontalDivider(
                        color = AppColor.divider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    AppOutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            newPasswordError = null
                            serverError = null
                        },
                        label = "새 비밀번호",
                        leadingIcon = Icons.Filled.Lock,
                        isPassword = true,
                        helperText = "8자 이상 입력해주세요.",
                        errorText = newPasswordError,
                        imeAction = ImeAction.Next
                    )
                    AppOutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            confirmPasswordError = null
                            serverError = null
                        },
                        label = "새 비밀번호 확인",
                        leadingIcon = Icons.Filled.Lock,
                        isPassword = true,
                        errorText = confirmPasswordError,
                        imeAction = ImeAction.Done
                    )
                    if (serverError != null) {
                        Text(
                            text = serverError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val hasChanges = currentPassword.isNotEmpty() ||
                             newPassword.isNotEmpty() ||
                             confirmPassword.isNotEmpty()

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green400)
                }
            } else {
                Button(
                    onClick = {
                        if (validate()) {
                            serverError = null
                            viewModel.changePassword(initialPhone, currentPassword, newPassword)
                        }
                    },
                    enabled = hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green400,
                        disabledContainerColor = Green400.copy(alpha = 0.4f),
                        disabledContentColor = BrandWhite.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "프로필 수정",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = AppColor.textTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = BrandWhite,
            shadowElevation = 1.dp
        ) {
            content()
        }
    }
}
