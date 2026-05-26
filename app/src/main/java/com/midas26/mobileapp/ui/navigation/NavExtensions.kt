package com.midas26.mobileapp.ui.navigation

import androidx.navigation.NavController

/**
 * 현재 백스택의 라우트가 [expectedRoute]와 일치할 때만 뒤로 이동합니다.
 *
 * 문제: IconButton 을 빠르게 두 번 탭하면 onBack 람다가 두 번 호출되어
 *       popBackStack() 이 연속으로 실행됩니다. 첫 번째 pop 으로 홈 화면에
 *       도달한 뒤 두 번째 pop 이 홈까지 제거해 하얀 화면이 나타납니다.
 *
 * 해결: pop 직전에 현재 라우트를 확인하여, 이미 이전 화면으로 전환된 경우
 *       두 번째 pop 을 무시합니다.
 */
fun NavController.popBackStackIfCurrent(expectedRoute: String) {
    if (currentBackStackEntry?.destination?.route == expectedRoute) {
        popBackStack()
    }
}
