package com.example.backend.Util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationStore {

    // 인증번호를 틀릴 수 있는 최대 횟수. 넘으면 인증번호를 폐기하고 다시 요청하게 한다.
    // (6자리 숫자를 무제한으로 대입해 보는 공격을 막기 위함)
    static final int MAX_FAILED_ATTEMPTS = 5;

    // 인증 완료 상태를 유지하는 시간. 이 시간이 지나면 다시 인증해야 한다.
    static final long VERIFIED_TTL_MILLIS = 10 * 60 * 1000L;

    private static final Map<String, String> pendingCodes = new ConcurrentHashMap<>();
    private static final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private static final Map<String, Long> verifiedAt = new ConcurrentHashMap<>();

    public static void saveCode(String phone, String code) {
        pendingCodes.put(phone, code);
        failedAttempts.remove(phone);
        verifiedAt.remove(phone);
    }

    public static boolean verifyCode(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }

        String stored = pendingCodes.get(phone);
        if (stored == null) {
            return false;
        }

        if (stored.equals(code)) {
            pendingCodes.remove(phone);
            failedAttempts.remove(phone);
            verifiedAt.put(phone, System.currentTimeMillis());
            return true;
        }

        // 틀린 횟수를 세고, 한도를 넘으면 인증번호를 폐기한다.
        int failures = failedAttempts.merge(phone, 1, Integer::sum);
        if (failures >= MAX_FAILED_ATTEMPTS) {
            pendingCodes.remove(phone);
            failedAttempts.remove(phone);
        }
        return false;
    }

    public static boolean isVerified(String phone) {
        return isVerified(phone, System.currentTimeMillis());
    }

    // 테스트에서 시간을 직접 넣어 만료를 확인할 수 있도록 분리
    static boolean isVerified(String phone, long nowMillis) {
        if (phone == null) {
            return false;
        }

        Long at = verifiedAt.get(phone);
        if (at == null) {
            return false;
        }

        if (nowMillis - at > VERIFIED_TTL_MILLIS) {
            verifiedAt.remove(phone);
            return false;
        }
        return true;
    }

    public static void clearVerified(String phone) {
        if (phone != null) {
            verifiedAt.remove(phone);
        }
    }
}
