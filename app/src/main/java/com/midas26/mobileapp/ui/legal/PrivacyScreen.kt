package com.midas26.mobileapp.ui.legal

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.midas26.mobileapp.ui.components.VerticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite

private data class PrivacyItem(
    val titleRes: Int,
    val descRes: Int,
    val detailTitle: String,
    val detailContent: String
)

@Composable
fun PrivacyScreen(
    onAgreeAndStart: () -> Unit
) {
    val items = listOf(
        PrivacyItem(
            titleRes = R.string.privacy_terms,
            descRes = R.string.privacy_terms_desc,
            detailTitle = "이용 약관 안내",
            detailContent = """
                제1조 목적
                본 약관은 똑똑 서비스의 이용 조건과 절차, 이용자와 서비스 제공자의 권리 및 의무를 안내하기 위한 것입니다.

                제2조 서비스 내용
                똑똑은 사용자의 음성 대화와 회상 과제 수행 결과를 바탕으로 인지 건강 상태를 확인할 수 있도록 돕는 서비스입니다. 분석 결과는 참고용 정보이며, 의학적 진단이나 치료를 대체하지 않습니다.

                제3조 이용자 의무
                이용자는 본인의 정확한 정보를 입력해야 하며, 타인의 정보를 무단으로 사용해서는 안 됩니다. 서비스 이용 중 타인의 권리를 침해하거나 서비스 운영을 방해하는 행위를 해서는 안 됩니다.

                제4조 서비스 변경 및 중단
                안정적인 서비스 제공을 위해 기능이 변경되거나 점검이 진행될 수 있습니다. 네트워크, 서버, 기기 환경에 따라 일부 기능 이용이 제한될 수 있습니다.

                제5조 책임 범위
                서비스는 인지 건강 관리에 도움을 주기 위한 보조 도구입니다. 건강 이상이 의심되는 경우 반드시 보호자 또는 전문 의료기관과 상담하시기 바랍니다.
            """.trimIndent()
        ),
        PrivacyItem(
            titleRes = R.string.privacy_personal,
            descRes = R.string.privacy_personal_desc,
            detailTitle = "개인정보 처리방침",
            detailContent = """
                수집하는 개인정보
                서비스 이용을 위해 이름, 생년월일, 전화번호, 비밀번호, 사용자 역할, 환자 연결 코드, 서비스 이용 기록이 수집될 수 있습니다.

                수집 및 이용 목적
                수집된 정보는 회원 식별, 로그인, 보호자와 사용자 연결, 분석 결과 제공, 서비스 안정성 개선을 위해 사용됩니다.

                보유 및 이용 기간
                개인정보는 회원 탈퇴 시까지 보관되며, 탈퇴 후에는 관련 법령에 따라 필요한 경우를 제외하고 지체 없이 삭제됩니다.

                제3자 제공
                사용자의 개인정보는 원칙적으로 외부에 제공하지 않습니다. 단, 법령에 따른 요청이 있거나 사용자가 동의한 경우에는 예외적으로 제공될 수 있습니다.

                이용자의 권리
                이용자는 언제든지 본인의 개인정보 열람, 수정, 삭제를 요청할 수 있습니다.
            """.trimIndent()
        ),
        PrivacyItem(
            titleRes = R.string.privacy_voice,
            descRes = R.string.privacy_voice_desc,
            detailTitle = "음성 데이터 수집 동의",
            detailContent = """
                수집하는 음성 데이터
                서비스 이용 중 사용자의 음성 녹음, 음성 인식 결과, 발화 속도, 침묵 구간, 반복 표현, 텍스트 분석 결과 등이 수집될 수 있습니다.

                수집 및 이용 목적
                음성 데이터는 인지 건강 분석, 회상 과제 분석, 점수 산출, 사용자별 변화 추적을 위해 사용됩니다.

                분석 결과 활용
                분석 결과는 앱 화면에서 사용자에게 제공되며, 사용자가 보호자와 연결된 경우 보호자에게도 일부 결과가 공유될 수 있습니다.

                보관 및 삭제
                음성 데이터와 분석 결과는 서비스 제공 목적에 필요한 기간 동안 보관되며, 회원 탈퇴 또는 삭제 요청 시 관련 법령에 따라 처리됩니다.

                동의 거부 안내
                음성 데이터 수집에 동의하지 않을 경우 음성 대화 및 인지 분석 기능 이용이 제한될 수 있습니다.
            """.trimIndent()
        )
    )

    val checked = remember {
        mutableStateListOf<Boolean>().apply {
            repeat(items.size) { add(false) }
        }
    }

    var selectedItem by remember { mutableStateOf<PrivacyItem?>(null) }

    val allChecked by remember {
        derivedStateOf { checked.all { it } }
    }

    val canProceed by remember {
        derivedStateOf { checked.all { it } }
    }

    selectedItem?.let { item ->
        TermsDetailDialog(
            title = item.detailTitle,
            content = item.detailContent,
            onDismiss = { selectedItem = null }
        )
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(AppColor.greenSurface)
            ) {
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .offset(x = 270.dp, y = 30.dp)
                        .clip(CircleShape)
                        .background(AppColor.greenSurfaceVariant.copy(alpha = 0.7f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-44).dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = AppColor.greenPrimary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrandWhite,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.privacy_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "필수 약관을 확인하고 동의해주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColor.textTertiary
                )

                Spacer(modifier = Modifier.height(28.dp))

                AgreeAllCard(
                    checked = allChecked,
                    onToggle = {
                        val nextValue = !allChecked
                        for (i in checked.indices) {
                            checked[i] = nextValue
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                items.forEachIndexed { idx, item ->
                    PrivacyRow(
                        item = item,
                        checked = checked[idx],
                        onToggle = { checked[idx] = !checked[idx] },
                        onDetailClick = { selectedItem = item }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                AppPrimaryButton(
                    text = stringResource(R.string.btn_agree_and_start),
                    onClick = onAgreeAndStart,
                    enabled = canProceed
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        VerticalScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun TermsDetailDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textSecondary,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "확인",
                    color = AppColor.greenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun AgreeAllCard(
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = AppColor.greenSurface,
        border = BorderStroke(2.5.dp, AppColor.greenPrimary)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                accent = AppColor.greenPrimary
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column {
                Text(
                    text = stringResource(R.string.privacy_agree_all),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.accentDark,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.privacy_agree_all_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.greenSecondary
                )
            }
        }
    }
}

@Composable
private fun PrivacyRow(
    item: PrivacyItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onDetailClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClick),
        shape = RoundedCornerShape(16.dp),
        color = BrandWhite,
        border = BorderStroke(1.5.dp, AppColor.divider)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                accent = AppColor.greenPrimary,
                modifier = Modifier.clickable(onClick = onToggle)
            )

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColor.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColor.textTertiary
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColor.greenSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.permission_required),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColor.greenPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.size(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "자세히 보기",
                tint = AppColor.textTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun Checkbox(
    checked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (checked) accent else BrandWhite,
        border = BorderStroke(2.dp, if (checked) accent else AppColor.divider)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = BrandWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}