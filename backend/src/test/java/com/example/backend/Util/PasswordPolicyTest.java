package com.example.backend.Util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 비밀번호 규칙(영문 + 숫자 포함, 8자 이상) 검증.
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("영문과 숫자를 포함한 8자 이상은 통과한다")
    void acceptsEightOrMoreCharactersWithLetterAndDigit() {
        assertTrue(PasswordPolicy.isValid("abcd1234"));
        assertTrue(PasswordPolicy.isValid("Midas2026!"));
    }

    @Test
    @DisplayName("8자 미만이면 거부한다")
    void rejectsShorterThanEightCharacters() {
        assertFalse(PasswordPolicy.isValid("abc1234"));
    }

    @Test
    @DisplayName("숫자만 있으면 거부한다")
    void rejectsDigitsOnly() {
        assertFalse(PasswordPolicy.isValid("12345678"));
    }

    @Test
    @DisplayName("영문만 있으면 거부한다")
    void rejectsLettersOnly() {
        assertFalse(PasswordPolicy.isValid("abcdefgh"));
    }

    @Test
    @DisplayName("한글과 숫자만 있으면 거부한다 (앱과 같은 규칙: 영문 필수)")
    void rejectsKoreanAndDigitsWithoutEnglishLetter() {
        assertFalse(PasswordPolicy.isValid("비밀번호12345678"));
    }

    @Test
    @DisplayName("값이 없으면 거부한다")
    void rejectsNullOrEmpty() {
        assertFalse(PasswordPolicy.isValid(null));
        assertFalse(PasswordPolicy.isValid(""));
    }

    @Test
    @DisplayName("규칙에 맞지 않으면 400 예외를 던진다")
    void validateThrowsBadRequestForWeakPassword() {
        ResponseStatusException e = assertThrows(
                ResponseStatusException.class,
                () -> PasswordPolicy.validate("12345678")
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals(PasswordPolicy.MESSAGE, e.getReason());
    }

    @Test
    @DisplayName("규칙에 맞으면 예외를 던지지 않는다")
    void validatePassesForStrongPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("abcd1234"));
    }
}
