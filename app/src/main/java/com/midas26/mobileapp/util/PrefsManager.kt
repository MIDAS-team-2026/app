package com.midas26.mobileapp.util

import android.content.Context

/**
 * 단순 SharedPreferences 래퍼.
 *
 * - 온보딩 노출 여부
 * - 로그인 토큰 저장/제거
 * - 사용자 역할 (user / guardian) 저장
 *
 * Compose 친화적인 접근을 위해 [PrefsManager.from]을 통해 컨텍스트 한 번만
 * 인스턴스화하여 ViewModel/Repository에서 주입받아 사용하도록 의도되어 있습니다.
 */
class PrefsManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingSeen() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    fun isLoggedIn(): Boolean = prefs.getString(KEY_TOKEN, null) != null

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun getUserRole(): String = prefs.getString(KEY_ROLE, ROLE_USER) ?: ROLE_USER

    fun saveUserRole(role: String) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    companion object {
        private const val PREF_NAME = "midas_prefs"
        private const val KEY_ONBOARDING = "seen_onboarding"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_ROLE = "user_role"

        const val ROLE_USER = "user"
        const val ROLE_GUARDIAN = "guardian"

        @Volatile private var INSTANCE: PrefsManager? = null

        fun from(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
