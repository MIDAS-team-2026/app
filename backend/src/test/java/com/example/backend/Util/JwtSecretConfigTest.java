package com.example.backend.Util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 서명 키를 환경변수(JWT_SECRET)에서 읽도록 바꾼 동작 검증.
 */
class JwtSecretConfigTest {

    private static final String SECRET_A = "test-secret-key-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SECRET_B = "test-secret-key-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    // 예전에 소스 코드에 그대로 적혀 있던(공개 저장소에 노출된) 키
    private static final String OLD_PUBLIC_KEY = "your-very-secret-key-should-be-very-long-and-secure";

    @Test
    @DisplayName("같은 키로 설정한 서버끼리는 서로의 토큰을 인정한다")
    void sameSecretAcceptsTokenAcrossInstances() {
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET_A);
        JwtTokenProvider verifier = new JwtTokenProvider(SECRET_A);

        String token = issuer.createToken("01012345678", "PATIENT");

        assertTrue(verifier.validateToken(token));
        assertEquals("01012345678", verifier.getPhoneFromToken(token));
        assertEquals("PATIENT", verifier.getRoleFromToken(token));
    }

    @Test
    @DisplayName("다른 키로 만든 토큰은 거부한다")
    void differentSecretRejectsToken() {
        String token = new JwtTokenProvider(SECRET_A).createToken("01012345678", "PATIENT");

        assertFalse(new JwtTokenProvider(SECRET_B).validateToken(token));
    }

    @Test
    @DisplayName("예전에 코드에 적혀 있던 키로 위조한 토큰은 더 이상 통하지 않는다")
    void tokenForgedWithOldHardcodedKeyIsRejected() {
        String forged = Jwts.builder()
                .setSubject("01099998888")
                .claim("role", "PROTECTOR")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(OLD_PUBLIC_KEY.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();

        assertFalse(new JwtTokenProvider(SECRET_A).validateToken(forged));
        assertFalse(new JwtTokenProvider("").validateToken(forged));
        assertFalse(new JwtTokenProvider().validateToken(forged));
    }

    @Test
    @DisplayName("키를 설정하지 않으면 실행할 때마다 다른 임시 키를 쓴다")
    void blankSecretUsesRandomKeyPerInstance() {
        JwtTokenProvider first = new JwtTokenProvider("");
        JwtTokenProvider second = new JwtTokenProvider("  ");

        String token = first.createToken("01012345678", "PATIENT");

        assertTrue(first.validateToken(token));
        assertFalse(second.validateToken(token));
    }

    @Test
    @DisplayName("너무 짧은 키는 서버 시작 단계에서 거부한다")
    void tooShortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("short-key"));
    }
}
