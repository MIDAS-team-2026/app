package com.midas26.mobileapp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 비밀번호 규칙(영문 + 숫자 포함, 8자 이상) 검증.
 * 서버의 PasswordPolicyTest 와 같은 사례를 써서 앱과 서버의 규칙이 어긋나지 않게 한다.
 */
class PasswordRulesTest {

    @Test
    fun acceptsEightOrMoreCharactersWithLetterAndDigit() {
        assertTrue(PasswordRules.isValid("abcd1234"))
        assertTrue(PasswordRules.isValid("Midas2026!"))
    }

    @Test
    fun rejectsShorterThanEightCharacters() {
        assertFalse(PasswordRules.isValid("abc1234"))
    }

    @Test
    fun rejectsDigitsOnly() {
        // 프로필 화면에서 예전에는 통과되던 경우 (8자 이상만 검사했음)
        assertFalse(PasswordRules.isValid("12345678"))
    }

    @Test
    fun rejectsLettersOnly() {
        assertFalse(PasswordRules.isValid("abcdefgh"))
    }

    @Test
    fun rejectsKoreanAndDigitsWithoutEnglishLetter() {
        assertFalse(PasswordRules.isValid("비밀번호12345678"))
    }

    @Test
    fun rejectsEmpty() {
        assertFalse(PasswordRules.isValid(""))
    }
}
