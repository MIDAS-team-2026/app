package com.midas26.mobileapp.util

import android.content.Context
import android.content.Intent
import com.midas26.mobileapp.location.LocationForegroundService

/**
 * 로그인 상태를 끝낼 때(로그아웃 · 회원 탈퇴 · 토큰 만료) 공통으로 해야 하는 정리.
 *
 * 토큰만 지우면 위치 공유 서비스가 계속 돌면서 5분마다 위치를 보내려 하고,
 * 다음에 다른 계정으로 로그인하면 그 계정의 위치가 전송된다.
 */
object SessionManager {

    fun signOut(context: Context) {
        val appContext = context.applicationContext
        val prefs = PrefsManager.from(appContext)

        // 위치 공유 중지 — 다음에 로그인한 계정이 모르는 사이에 위치를 공유하지 않도록 설정도 끈다.
        appContext.stopService(Intent(appContext, LocationForegroundService::class.java))
        prefs.setLocationSharingEnabled(false)

        // 로그인 정보 삭제
        prefs.clearToken()
        prefs.saveUserId(-1)
    }
}
