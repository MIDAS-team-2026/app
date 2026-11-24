package com.midas26.mobileapp.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 401 응답을 받았을 때 강제 로그아웃할지 판단하는 규칙 검증.
 */
class ForceLogoutPolicyTest {

    @Test
    fun expiredTokenOnProtectedApiForcesLogout() {
        assertTrue(shouldForceLogout(401, "/api/ai/analysis/user/1/latest", hadToken = true))
        assertTrue(shouldForceLogout(401, "/api/location", hadToken = true))
    }

    @Test
    fun wrongPasswordOnLoginDoesNotForceLogout() {
        // 로그인 화면에서 비밀번호를 틀린 경우
        assertFalse(shouldForceLogout(401, "/api/auth/login", hadToken = false))
        // 프로필에서 현재 비밀번호를 틀린 경우 (로그인 상태라 토큰이 있음)
        assertFalse(shouldForceLogout(401, "/api/auth/login", hadToken = true))
    }

    @Test
    fun requestWithoutTokenDoesNotForceLogout() {
        // 로그아웃 뒤 남아 있던 백그라운드 요청이 401 을 받아도 화면을 옮기지 않는다
        assertFalse(shouldForceLogout(401, "/api/location", hadToken = false))
    }

    @Test
    fun otherStatusCodesDoNotForceLogout() {
        assertFalse(shouldForceLogout(200, "/api/location", hadToken = true))
        assertFalse(shouldForceLogout(403, "/api/auth/reset-password", hadToken = true))
        assertFalse(shouldForceLogout(404, "/api/location/current/1", hadToken = true))
        assertFalse(shouldForceLogout(500, "/api/voice/upload", hadToken = true))
    }
}
