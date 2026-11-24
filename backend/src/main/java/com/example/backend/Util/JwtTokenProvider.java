package com.example.backend.Util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    // HS256 서명에 필요한 최소 키 길이 (256비트 = 32바이트)
    static final int MIN_SECRET_BYTES = 32;

    private final Key signingKey;
    private final long validityInMilliseconds = 1728000000L; // 20일

    // 서명 키를 지정하지 않고 만들 때(테스트 등): 실행할 때마다 새로 만든 임시 키를 쓴다.
    public JwtTokenProvider() {
        this("");
    }

    // 서명 키는 코드에 적어 두지 않고 환경변수 JWT_SECRET(설정 jwt.secret)에서 읽는다.
    // 저장소에 키가 그대로 있으면 누구나 그 키로 다른 사용자의 토큰을 만들어낼 수 있기 때문이다.
    @Autowired
    public JwtTokenProvider(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            // 키가 설정되지 않았으면 서버를 띄울 때마다 무작위 키를 만든다.
            // (서버를 재시작하면 기존 토큰이 무효가 되어 다시 로그인해야 한다.)
            this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            log.warn("JWT_SECRET 이 설정되지 않아 임시 서명 키를 사용합니다. "
                    + "서버를 재시작하면 기존 로그인 토큰이 무효가 됩니다.");
            return;
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. (현재 "
                            + secretBytes.length + "바이트)");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String createToken(String phone, String role) {
        Claims claims = Jwts.claims().setSubject(phone);
        claims.put("role", role);

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getPhoneFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
