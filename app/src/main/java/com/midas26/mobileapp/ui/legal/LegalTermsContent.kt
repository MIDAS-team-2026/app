package com.midas26.mobileapp.ui.legal

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.ui.theme.BrandWhite

data class LegalTermsItem(
    val title: String,
    val detailTitle: String,
    val detailContent: String
)

val LegalTermsItems = listOf(
    LegalTermsItem(
        title = "이용 약관 동의",
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
    LegalTermsItem(
        title = "개인정보 처리방침",
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
    LegalTermsItem(
        title = "음성 데이터 수집 동의",
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

@Composable
fun LegalTermsDetailDialog(
    item: LegalTermsItem,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = item.detailTitle,
                style = MaterialTheme.typography.titleLarge,
                color = AppColor.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = item.detailContent,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColor.textSecondary,
                lineHeight = 22.sp,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
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
        },
        containerColor = BrandWhite
    )
}