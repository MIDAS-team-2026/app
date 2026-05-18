package com.midas26.mobileapp.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.components.AppOutlinedTextField
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.Gray100
import com.midas26.mobileapp.ui.theme.Gray200
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800
import com.midas26.mobileapp.ui.theme.Green400
import com.midas26.mobileapp.ui.theme.Green50
import com.midas26.mobileapp.ui.theme.Green600
import com.midas26.mobileapp.ui.theme.AppColor

@Composable
fun ProfileEditScreen(
    initialName: String = "홍길동",
    initialPhone: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var valid = true
        nameError = if (name.isBlank()) { valid = false; "이름을 입력해주세요" } else null

        val changingPassword = currentPassword.isNotEmpty() || newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
        if (changingPassword) {
            newPasswordError = when {
                newPassword.length < 8 -> { valid = false; "8자 이상 입력해주세요" }
                else -> null
            }
            confirmPasswordError = when {
                newPassword != confirmPassword -> { valid = false; "비밀번호가 일치하지 않아요" }
                else -> null
            }
        } else {
            newPasswordError = null
            confirmPasswordError = null
        }
        return valid
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
                    .padding(top = 48.dp, bottom = 12.dp, start = 4.dp, end = 8.dp)
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
                    text = "프로필 편집",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                TextButton(
                    onClick = {
                        if (validate()) {
                            Toast.makeText(context, "저장되었어요", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text(
                        text = "저장",
                        color = AppColor.accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
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
                            text = name.take(1).ifEmpty { "?" },
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColor.accentDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 기본 정보 섹션
            ProfileSection(title = "기본 정보") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AppOutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameError = null },
                        label = "이름",
                        errorText = nameError,
                        imeAction = ImeAction.Next
                    )
                    AppOutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "전화번호",
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 비밀번호 변경 섹션
            ProfileSection(title = "비밀번호 변경") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AppOutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = "현재 비밀번호",
                        isPassword = true,
                        imeAction = ImeAction.Next
                    )
                    HorizontalDivider(
                        color = AppColor.divider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    AppOutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; newPasswordError = null },
                        label = "새 비밀번호",
                        isPassword = true,
                        helperText = "영문, 숫자 포함 8자 이상",
                        errorText = newPasswordError,
                        imeAction = ImeAction.Next
                    )
                    AppOutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; confirmPasswordError = null },
                        label = "새 비밀번호 확인",
                        isPassword = true,
                        errorText = confirmPasswordError,
                        imeAction = ImeAction.Done
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (validate()) {
                        Toast.makeText(context, "저장되었어요", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green400)
            ) {
                Text(
                    text = "저장하기",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandWhite
                )
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
