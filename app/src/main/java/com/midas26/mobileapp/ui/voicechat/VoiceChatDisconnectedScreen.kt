package com.midas26.mobileapp.ui.voicechat

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.BrandWhite
import com.midas26.mobileapp.ui.theme.AppColor

@Composable
fun VoiceChatDisconnectedScreen(
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandWhite)
    ) {
        VoiceChatTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AppColor.greenSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = AppColor.accentDark,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "연결이 끊겼어요",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "네트워크 상태를 확인하고\n다시 시도해주세요",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            // 자동 저장 안내 카드
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = AppColor.greenSurface
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "자동 저장됨",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColor.accentDark,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "녹음한 음성은 안전하게\n저장되었어요. 연결되면 자동으로\n분석이 이어집니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.textSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 홈으로 (보조 — 흰 배경 + 녹색 텍스트 + 회색 테두리)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable(onClick = onGoHome),
                shape = RoundedCornerShape(16.dp),
                color = BrandWhite,
                border = androidx.compose.foundation.BorderStroke(2.dp, AppColor.divider)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "홈으로",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColor.accentDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // 다시 시도 (Primary)
            Box(modifier = Modifier.weight(1f)) {
                AppPrimaryButton(text = "다시 시도", onClick = onRetry)
            }
        }
    }
}
