package com.midas26.mobileapp.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SharedPreferences 관리 클래스.
 *
 * 저장 정보:
 * - 온보딩 확인 여부
 * - 로그인 토큰
 * - 사용자 역할
 * - 사용자 기본 정보
 * - 알림 설정
 * - 접근성 설정
 * - 보호자와 사용자 관계
 * - 사용자별 최초 튜토리얼 완료 여부
 */
class PrefsManager private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    // =========================================================
    // 온보딩
    // =========================================================

    fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING, false)
    }

    fun setOnboardingSeen() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING, true)
            .apply()
    }

    // =========================================================
    // 튜토리얼
    // =========================================================

    /**
     * 현재 로그인한 사용자에게 대응하는 튜토리얼 완료 키를 반환합니다.
     *
     * 동일한 기기에서 여러 일반 사용자가 로그인할 수 있으므로,
     * 서버 사용자 ID가 있으면 사용자별 완료 상태를 따로 저장합니다.
     *
     * 사용자 ID가 아직 저장되지 않은 예외 상황에서는 기존 공용 키를
     * 사용하여 앱이 비정상 종료되지 않도록 처리합니다.
     */
    private fun getTutorialCompletedKey(): String {
        val userId = getUserId()

        return if (userId > 0) {
            "$KEY_TUTORIAL_COMPLETED_PREFIX$userId"
        } else {
            KEY_TUTORIAL_COMPLETED_FALLBACK
        }
    }

    /**
     * 현재 로그인한 사용자가 앱 사용법 튜토리얼을 완료했는지 확인합니다.
     */
    fun hasCompletedTutorial(): Boolean {
        return prefs.getBoolean(
            getTutorialCompletedKey(),
            false
        )
    }

    /**
     * 현재 로그인한 사용자의 튜토리얼 완료 여부를 저장합니다.
     *
     * 완료 버튼뿐 아니라 '건너뛰기'를 선택했을 때도 true를 저장하면
     * 다음 로그인부터 자동으로 다시 나타나지 않습니다.
     */
    fun setTutorialCompleted(completed: Boolean = true) {
        prefs.edit()
            .putBoolean(
                getTutorialCompletedKey(),
                completed
            )
            .apply()
    }

    /**
     * 튜토리얼을 자동으로 시작해야 하는지 확인합니다.
     *
     * 다음 조건을 모두 만족할 때만 true를 반환합니다.
     * - 로그인 토큰이 존재함
     * - 일반 사용자(PATIENT) 계정임
     * - 튜토리얼을 아직 완료하지 않음
     *
     * 보호자(PROTECTOR) 계정에서는 항상 false입니다.
     */
    fun shouldStartTutorial(): Boolean {
        return isLoggedIn() &&
                getUserRole() == ROLE_USER &&
                !hasCompletedTutorial()
    }

    /**
     * 이전 코드와의 호환성을 위해 유지하는 함수입니다.
     *
     * '앱 사용법 다시 보기'는 최초 튜토리얼 완료 기록을 변경하면 안 되므로
     * 이 함수는 더 이상 완료 여부를 false로 초기화하지 않습니다.
     *
     * 다시 보기는 TutorialViewModel의 수동 실행 함수만 호출해야 합니다.
     */
    @Deprecated(
        message = "다시 보기에서는 완료 기록을 초기화하지 않습니다. TutorialViewModel의 수동 실행 함수를 사용하세요."
    )
    fun resetTutorial() {
        // 의도적으로 아무 작업도 하지 않습니다.
    }

    // =========================================================
    // 로그인
    // =========================================================

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveToken(token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    /**
     * 로그아웃할 때 로그인 토큰만 제거합니다.
     *
     * 튜토리얼 완료 여부는 유지되기 때문에
     * 같은 앱에서 다시 로그인해도 튜토리얼이 자동으로 반복 실행되지 않습니다.
     */
    fun clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .apply()
    }

    // =========================================================
    // 사용자 역할
    // =========================================================

    fun getUserRole(): String {
        return prefs.getString(KEY_ROLE, ROLE_USER) ?: ROLE_USER
    }

    fun saveUserRole(role: String) {
        prefs.edit()
            .putString(KEY_ROLE, role)
            .apply()
    }

    // =========================================================
    // 사용자 정보
    // =========================================================

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    fun saveUserName(name: String) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    fun getUserPhone(): String {
        return prefs.getString(KEY_USER_PHONE, "") ?: ""
    }

    fun saveUserPhone(phone: String) {
        prefs.edit()
            .putString(KEY_USER_PHONE, phone)
            .apply()
    }

    /**
     * 서버에서 사용하는 사용자 PK입니다.
     */
    fun getUserId(): Int {
        return prefs.getInt(KEY_USER_ID, -1)
    }

    fun saveUserId(id: Int) {
        prefs.edit()
            .putInt(KEY_USER_ID, id)
            .apply()
    }

    fun getUserCode(): String {
        return prefs.getString(KEY_USER_CODE, "") ?: ""
    }

    fun saveUserCode(code: String) {
        prefs.edit()
            .putString(KEY_USER_CODE, code)
            .apply()
    }

    // =========================================================
    // 음성 대화
    // =========================================================

    fun getLastSessionId(): Long {
        return prefs.getLong(KEY_LAST_SESSION_ID, -1L)
    }

    fun saveLastSessionId(id: Long) {
        prefs.edit()
            .putLong(KEY_LAST_SESSION_ID, id)
            .apply()
    }

    // =========================================================
    // 위치 공유
    // =========================================================

    fun getLocationSharingEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCATION_SHARING, false)
    }

    fun setLocationSharingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LOCATION_SHARING, enabled)
            .apply()
    }

    // =========================================================
    // 알림
    // =========================================================

    fun getNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIF_ENABLED, true)
    }

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_NOTIF_ENABLED, enabled)
            .apply()
    }

    fun getNotificationHour(): Int {
        return prefs.getInt(KEY_NOTIF_HOUR, 8)
    }

    fun getNotificationMinute(): Int {
        return prefs.getInt(KEY_NOTIF_MINUTE, 0)
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_NOTIF_HOUR, hour)
            .putInt(KEY_NOTIF_MINUTE, minute)
            .apply()
    }

    // =========================================================
    // 접근성 설정
    // =========================================================

    fun getAccessibilityFontSize(): Int {
        return prefs.getInt(KEY_FONT_SIZE, 1)
    }

    fun setAccessibilityFontSize(level: Int) {
        prefs.edit()
            .putInt(KEY_FONT_SIZE, level)
            .apply()
    }

    fun getHighContrast(): Boolean {
        return prefs.getBoolean(KEY_HIGH_CONTRAST, false)
    }

    fun setHighContrast(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_HIGH_CONTRAST, enabled)
            .apply()
    }

    fun getTtsSpeed(): Float {
        return prefs.getFloat(KEY_TTS_SPEED, 1.0f)
    }

    fun setTtsSpeed(speed: Float) {
        prefs.edit()
            .putFloat(KEY_TTS_SPEED, speed)
            .apply()
    }

    fun getVoiceChatEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOICE_CHAT_ENABLED, true)
    }

    fun setVoiceChatEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_VOICE_CHAT_ENABLED, enabled)
            .apply()
    }

    fun getTapToReplay(): Boolean {
        return prefs.getBoolean(KEY_TAP_TO_REPLAY, true)
    }

    fun setTapToReplay(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_TAP_TO_REPLAY, enabled)
            .apply()
    }

    fun getHapticFeedback(): Boolean {
        return prefs.getBoolean(KEY_HAPTIC, true)
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_HAPTIC, enabled)
            .apply()
    }

    fun getLargeTouchArea(): Boolean {
        return prefs.getBoolean(KEY_LARGE_TOUCH, false)
    }

    fun setLargeTouchArea(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LARGE_TOUCH, enabled)
            .apply()
    }

    // =========================================================
    // 보호자와 사용자 관계
    // =========================================================

    fun getPatientRelation(patientId: Int): String {
        return prefs.getString(
            "$KEY_PATIENT_RELATION_PREFIX$patientId",
            ""
        ) ?: ""
    }

    fun savePatientRelation(patientId: Int, relation: String) {
        prefs.edit()
            .putString(
                "$KEY_PATIENT_RELATION_PREFIX$patientId",
                relation
            )
            .apply()
    }

    fun removePatientRelation(patientId: Int) {
        prefs.edit()
            .remove("$KEY_PATIENT_RELATION_PREFIX$patientId")
            .apply()
    }

    // =========================================================
    // 보호자 분석 결과 확인 여부
    // =========================================================

    /**
     * 보호자가 오늘 환자의 분석 결과를 확인했음을 저장합니다.
     *
     * LocalDate는 API 26 이상에서 지원되므로,
     * minSdk 24에서도 동작하도록 SimpleDateFormat을 사용합니다.
     */
    fun markPatientResultViewedToday(patientId: Int) {
        prefs.edit()
            .putString(
                "$KEY_PATIENT_VIEWED_PREFIX$patientId",
                getTodayDateString()
            )
            .apply()
    }

    fun hasViewedPatientResultToday(patientId: Int): Boolean {
        val savedDate = prefs.getString(
            "$KEY_PATIENT_VIEWED_PREFIX$patientId",
            null
        ) ?: return false

        return savedDate == getTodayDateString()
    }

    /**
     * yyyy-MM-dd 형식으로 현재 날짜를 반환합니다.
     *
     * 예: 2026-07-30
     */
    private fun getTodayDateString(): String {
        val formatter = SimpleDateFormat(
            DATE_FORMAT,
            Locale.getDefault()
        )

        return formatter.format(Date())
    }

    companion object {

        private const val PREF_NAME = "midas_prefs"

        // 온보딩
        private const val KEY_ONBOARDING = "seen_onboarding"

        // 튜토리얼
        private const val KEY_TUTORIAL_COMPLETED_PREFIX =
            "tutorial_completed_user_"

        /*
         * 로그인 직후 사용자 ID가 아직 저장되지 않은 예외 상황에서만
         * 사용하는 공용 대체 키입니다.
         */
        private const val KEY_TUTORIAL_COMPLETED_FALLBACK =
            "tutorial_completed"

        // 로그인
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_ROLE = "user_role"

        // 사용자 정보
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_CODE = "user_code"

        // 음성 대화
        private const val KEY_LAST_SESSION_ID =
            "last_session_id"

        // 알림
        private const val KEY_NOTIF_ENABLED =
            "notif_enabled"

        private const val KEY_NOTIF_HOUR =
            "notif_hour"

        private const val KEY_NOTIF_MINUTE =
            "notif_minute"

        // 위치 공유
        private const val KEY_LOCATION_SHARING =
            "location_sharing_enabled"

        // 접근성
        private const val KEY_FONT_SIZE =
            "a11y_font_size"

        private const val KEY_HIGH_CONTRAST =
            "a11y_high_contrast"

        private const val KEY_TTS_SPEED =
            "a11y_tts_speed"

        private const val KEY_HAPTIC =
            "a11y_haptic"

        private const val KEY_LARGE_TOUCH =
            "a11y_large_touch"

        private const val KEY_VOICE_CHAT_ENABLED =
            "a11y_voice_chat_enabled"

        private const val KEY_TAP_TO_REPLAY =
            "a11y_tap_to_replay"

        // 보호자와 사용자 관계
        private const val KEY_PATIENT_RELATION_PREFIX =
            "patient_relation_"

        private const val KEY_PATIENT_VIEWED_PREFIX =
            "patient_viewed_date_"

        private const val DATE_FORMAT =
            "yyyy-MM-dd"

        const val ROLE_USER = "PATIENT"
        const val ROLE_GUARDIAN = "PROTECTOR"

        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun from(context: Context): PrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(
                    context.applicationContext
                ).also {
                    INSTANCE = it
                }
            }
        }
    }
}