package com.example.backend.Util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인증번호 저장소 검증.
 * 테스트마다 서로 다른 전화번호를 써서 서로 영향을 주지 않게 한다.
 */
class VerificationStoreTest {

    @Test
    @DisplayName("올바른 인증번호를 입력하면 인증 완료 상태가 된다")
    void correctCodeMarksPhoneAsVerified() {
        String phone = "01000000001";
        VerificationStore.saveCode(phone, "123456");

        assertFalse(VerificationStore.isVerified(phone));
        assertTrue(VerificationStore.verifyCode(phone, "123456"));
        assertTrue(VerificationStore.isVerified(phone));
    }

    @Test
    @DisplayName("틀린 인증번호는 인증되지 않는다")
    void wrongCodeIsRejected() {
        String phone = "01000000002";
        VerificationStore.saveCode(phone, "123456");

        assertFalse(VerificationStore.verifyCode(phone, "000000"));
        assertFalse(VerificationStore.isVerified(phone));
    }

    @Test
    @DisplayName("인증번호를 요청하지 않은 번호는 인증되지 않는다")
    void phoneWithoutPendingCodeIsRejected() {
        assertFalse(VerificationStore.verifyCode("01000000003", "123456"));
        assertFalse(VerificationStore.isVerified("01000000003"));
    }

    @Test
    @DisplayName("한 번 쓴 인증번호는 다시 쓸 수 없다")
    void codeCannotBeUsedTwice() {
        String phone = "01000000004";
        VerificationStore.saveCode(phone, "123456");

        assertTrue(VerificationStore.verifyCode(phone, "123456"));
        assertFalse(VerificationStore.verifyCode(phone, "123456"));
    }

    @Test
    @DisplayName("5번 틀리면 인증번호가 폐기되어 올바른 번호도 통하지 않는다")
    void codeIsDiscardedAfterTooManyFailedAttempts() {
        String phone = "01000000005";
        VerificationStore.saveCode(phone, "123456");

        for (int i = 0; i < VerificationStore.MAX_FAILED_ATTEMPTS; i++) {
            assertFalse(VerificationStore.verifyCode(phone, "000000"));
        }

        assertFalse(VerificationStore.verifyCode(phone, "123456"));
        assertFalse(VerificationStore.isVerified(phone));
    }

    @Test
    @DisplayName("4번까지 틀려도 5번째에 맞히면 인증된다")
    void correctCodeStillWorksBeforeTheAttemptLimit() {
        String phone = "01000000006";
        VerificationStore.saveCode(phone, "123456");

        for (int i = 0; i < VerificationStore.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertFalse(VerificationStore.verifyCode(phone, "000000"));
        }

        assertTrue(VerificationStore.verifyCode(phone, "123456"));
    }

    @Test
    @DisplayName("인증번호를 다시 요청하면 틀린 횟수와 인증 상태가 초기화된다")
    void requestingNewCodeResetsAttemptsAndVerifiedState() {
        String phone = "01000000007";
        VerificationStore.saveCode(phone, "123456");
        for (int i = 0; i < VerificationStore.MAX_FAILED_ATTEMPTS; i++) {
            VerificationStore.verifyCode(phone, "000000");
        }

        VerificationStore.saveCode(phone, "654321");

        assertFalse(VerificationStore.isVerified(phone));
        assertTrue(VerificationStore.verifyCode(phone, "654321"));
    }

    @Test
    @DisplayName("인증 완료 상태는 10분이 지나면 만료된다")
    void verifiedStateExpiresAfterTtl() {
        String phone = "01000000008";
        VerificationStore.saveCode(phone, "123456");
        VerificationStore.verifyCode(phone, "123456");

        long now = System.currentTimeMillis();

        assertTrue(VerificationStore.isVerified(phone, now + VerificationStore.VERIFIED_TTL_MILLIS - 60_000));
        assertFalse(VerificationStore.isVerified(phone, now + VerificationStore.VERIFIED_TTL_MILLIS + 60_000));
        // 만료된 뒤에는 기록이 지워져, 현재 시각으로 다시 물어도 인증되지 않은 상태다.
        assertFalse(VerificationStore.isVerified(phone));
    }

    @Test
    @DisplayName("인증 완료 상태를 지우면 다시 인증해야 한다")
    void clearedVerifiedStateRequiresVerificationAgain() {
        String phone = "01000000009";
        VerificationStore.saveCode(phone, "123456");
        VerificationStore.verifyCode(phone, "123456");

        VerificationStore.clearVerified(phone);

        assertFalse(VerificationStore.isVerified(phone));
    }

    @Test
    @DisplayName("전화번호나 인증번호가 없어도 오류 없이 거부한다")
    void nullInputsAreRejectedWithoutError() {
        assertFalse(VerificationStore.verifyCode(null, "123456"));
        assertFalse(VerificationStore.verifyCode("01000000010", null));
        assertFalse(VerificationStore.isVerified(null));
        VerificationStore.clearVerified(null);
    }
}
