package com.midas26.mobileapp.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.components.AppPrimaryButton
import com.midas26.mobileapp.ui.components.AppTextButton
import com.midas26.mobileapp.ui.theme.AppColor
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector
)

private val onboardingPages = listOf(
    OnboardingPage(R.string.onboarding_1_title, R.string.onboarding_1_desc, Icons.Filled.Chat),
    OnboardingPage(R.string.onboarding_2_title, R.string.onboarding_2_desc, Icons.Filled.Psychology),
    OnboardingPage(R.string.onboarding_3_title, R.string.onboarding_3_desc, Icons.Filled.FamilyRestroom)
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()

    val currentPage = pagerState.currentPage
    val isLast = currentPage == onboardingPages.lastIndex

    fun finish() {
        PrefsManager.from(context).setOnboardingSeen()
        onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 상단 Skip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isLast) {
                AppTextButton(
                    text = stringResource(R.string.btn_skip),
                    onClick = { finish() }
                )
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // ViewPager 영역
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }

        // 인디케이터 (3개 점) — 노년층 시인성을 위해 8dp→12dp / 24dp→32dp 로 확대
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            onboardingPages.forEachIndexed { index, _ ->
                val isActive = index == currentPage
                val width by animateDpAsState(
                    targetValue = if (isActive) 32.dp else 12.dp,
                    label = "dotWidth"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .height(12.dp)
                        .width(width)
                        .clip(if (isActive) RoundedCornerShape(6.dp) else CircleShape)
                        .background(if (isActive) AppColor.accent else AppColor.divider)
                )
            }
        }

        // 다음 / 시작하기 버튼
        AppPrimaryButton(
            text = if (isLast) stringResource(R.string.btn_start) else stringResource(R.string.btn_next),
            onClick = {
                if (isLast) {
                    finish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        )
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(48.dp),
            color = AppColor.greenSurface,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = AppColor.accent,
                    modifier = Modifier.size(128.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = AppColor.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(page.descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColor.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
