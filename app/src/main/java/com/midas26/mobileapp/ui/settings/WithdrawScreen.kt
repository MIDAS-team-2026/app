package com.midas26.mobileapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite

@Composable
fun WithdrawScreen(
    phone: String,
    onBack: () -> Unit,
    onSendCode: () -> Unit
) {
    // 전화번호 마스킹: 010-1234-5678 → 010-****-5678
    val maskedPhone = maskPhone(phone)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = AppColor.textPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "회원탈퇴",
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "탈퇴하기 전에 아래 내용을 확인해주세요.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColor.textTertiary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 경고 박스
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AppColor.errorPrimary.copy(alpha = 0.08f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "⚠️ 탈퇴 시 주의사항",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.errorPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "모든 대화 기록과 분석 데이터가 삭제됩니다.",
                    "삭제된 데이터는 복구할 수 없습니다.",
                    "연동된 보호자와의 연결이 해제됩니다."
                ).forEach { text ->
                    Text(
                        text = "• $text",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.errorPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 인증 전화번호 표시
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AppColor.surfaceElevated
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "인증번호를 받을 전화번호",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = maskedPhone,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColor.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSendCode,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColor.errorPrimary)
        ) {
            Text(
                text = "인증번호 발송",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = BrandWhite
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun maskPhone(phone: String): String {
    // 숫자만 추출
    val digits = phone.filter { it.isDigit() }
    return if (digits.length == 11) {
        "${digits.substring(0, 3)}-****-${digits.substring(7)}"
    } else {
        phone
    }
}
