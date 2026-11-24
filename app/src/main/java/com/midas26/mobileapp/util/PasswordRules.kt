package com.midas26.mobileapp.util

/**
 * 비밀번호 규칙.
 *
 * 회원가입 · 비밀번호 재설정 · 프로필 비밀번호 변경 화면이 모두 이 규칙 하나를 쓴다.
 * 서버(backend 의 PasswordPolicy)도 같은 규칙으로 검사하므로, 규칙을 바꿀 때는 양쪽을 함께 바꿔야 한다.
 *
 * 규칙: 영문(A-Z, a-z)과 숫자를 각각 1자 이상 포함하고, 8자 이상.
 */
object PasswordRules {

    const val MIN_LENGTH = 8

    fun isValid(password: String): Boolean {
        val hasEnglish = password.any { it in 'A'..'Z' || it in 'a'..'z' }
        val hasDigit = password.any { it.isDigit() }
        return password.length >= MIN_LENGTH && hasEnglish && hasDigit
    }
}
