package com.example.backend.Util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationStore {

    private static final Map<String, String> pendingCodes = new ConcurrentHashMap<>();
    private static final Set<String> verified = ConcurrentHashMap.newKeySet();

    public static void saveCode(String phone, String code) {
        pendingCodes.put(phone, code);
        verified.remove(phone);
    }

    public static boolean verifyCode(String phone, String code) {
        String stored = pendingCodes.get(phone);
        if (stored != null && stored.equals(code)) {
            pendingCodes.remove(phone);
            verified.add(phone);
            return true;
        }
        return false;
    }

    public static boolean isVerified(String phone) {
        return verified.contains(phone);
    }

    public static void clearVerified(String phone) {
        verified.remove(phone);
    }
}
