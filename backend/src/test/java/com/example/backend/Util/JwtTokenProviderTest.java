package com.example.backend.Util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 발급·검증 로직 검증.
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

    @Test
    @DisplayName("발급한 토큰은 검증을 통과하고 전화번호와 역할을 돌려준다")
    void issuedTokenIsValidAndCarriesPhoneAndRole() {
        String token = jwtTokenProvider.createToken("01012345678", "PATIENT");

        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("01012345678", jwtTokenProvider.getPhoneFromToken(token));
        assertEquals("PATIENT", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("서명이 변조된 토큰은 거부한다")
    void tokenWithTamperedSignatureIsRejected() {
        String token = jwtTokenProvider.createToken("01012345678", "PATIENT");

        // 토큰은 header.payload.signature 형식 — 서명의 첫 글자를 바꾼다.
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        String tampered = token.substring(0, signatureStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        assertFalse(jwtTokenProvider.validateToken(tampered));
    }

    @Test
    @DisplayName("다른 사람 번호로 내용을 바꾼 토큰은 거부한다")
    void tokenWithSwappedPayloadIsRejected() {
        String mine = jwtTokenProvider.createToken("01012345678", "PATIENT");
        String others = jwtTokenProvider.createToken("01099998888", "PATIENT");

        // 내 토큰의 서명은 그대로 두고, 내용(payload)만 다른 사람 것으로 바꾼다.
        String[] minePart = mine.split("\\.");
        String[] othersPart = others.split("\\.");
        String forged = minePart[0] + "." + othersPart[1] + "." + minePart[2];

        assertFalse(jwtTokenProvider.validateToken(forged));
    }

    @Test
    @DisplayName("형식이 잘못된 토큰은 거부한다")
    void malformedTokenIsRejected() {
        assertFalse(jwtTokenProvider.validateToken("not-a-jwt"));
        assertFalse(jwtTokenProvider.validateToken("aaa.bbb.ccc"));
    }

    @Test
    @DisplayName("비어 있는 토큰은 거부한다")
    void emptyTokenIsRejected() {
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }
}
