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

    // 사용자 이름
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun saveUserName(name: String) { prefs.edit().putString(KEY_USER_NAME, name).apply() }

    // 사용자 코드
    fun getUserCode(): String = prefs.getString(KEY_USER_CODE, "") ?: ""
    fun saveUserCode(code: String) { prefs.edit().putString(KEY_USER_CODE, code).apply() }

    // 접근성 설정
    fun getAccessibilityFontSize(): Int = prefs.getInt(KEY_FONT_SIZE, 1)
    fun setAccessibilityFontSize(level: Int) { prefs.edit().putInt(KEY_FONT_SIZE, level).apply() }

    fun getHighContrast(): Boolean = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    fun setHighContrast(enabled: Boolean) { prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply() }

    fun getTtsSpeed(): Float = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
    fun setTtsSpeed(speed: Float) { prefs.edit().putFloat(KEY_TTS_SPEED, speed).apply() }

    fun getVoiceChatEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_CHAT_ENABLED, true)
    fun setVoiceChatEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_VOICE_CHAT_ENABLED, enabled).apply() }

    fun getTapToReplay(): Boolean = prefs.getBoolean(KEY_TAP_TO_REPLAY, true)
    fun setTapToReplay(enabled: Boolean) { prefs.edit().putBoolean(KEY_TAP_TO_REPLAY, enabled).apply() }

    fun getHapticFeedback(): Boolean = prefs.getBoolean(KEY_HAPTIC, true)
    fun setHapticFeedback(enabled: Boolean) { prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply() }

    fun getLargeTouchArea(): Boolean = prefs.getBoolean(KEY_LARGE_TOUCH, false)
    fun setLargeTouchArea(enabled: Boolean) { prefs.edit().putBoolean(KEY_LARGE_TOUCH, enabled).apply() }

    companion object {
        private const val PREF_NAME = "midas_prefs"
        private const val KEY_ONBOARDING = "seen_onboarding"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_ROLE = "user_role"

        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_CODE = "user_code"

        // 접근성
        private const val KEY_FONT_SIZE = "a11y_font_size"
        private const val KEY_HIGH_CONTRAST = "a11y_high_contrast"
        private const val KEY_TTS_SPEED = "a11y_tts_speed"
        private const val KEY_HAPTIC = "a11y_haptic"
        private const val KEY_LARGE_TOUCH = "a11y_large_touch"
        private const val KEY_VOICE_CHAT_ENABLED = "a11y_voice_chat_enabled"
        private const val KEY_TAP_TO_REPLAY = "a11y_tap_to_replay"

        const val ROLE_USER = "user"
        const val ROLE_GUARDIAN = "guardian"

        @Volatile private var INSTANCE: PrefsManager? = null

        fun from(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
