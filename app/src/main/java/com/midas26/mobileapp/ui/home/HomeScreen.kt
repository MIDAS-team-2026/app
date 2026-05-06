package com.midas26.mobileapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midas26.mobileapp.R
import com.midas26.mobileapp.ui.theme.Gray400
import com.midas26.mobileapp.ui.theme.Gray800

/**
 * 홈 화면 자리표시자.
 * zip의 nav_graph.xml에서 homeFragment가 "다음 단계에서 구현"으로 표기되어 있어
 * 동일한 의미로 비워둡니다.
 */
@Composable
fun HomeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = Gray800,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "홈 화면은 다음 단계에서 구현됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray400
            )
        }
    }
}
