package com.example.backend.Util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 비밀번호 규칙.
 * 앱의 회원가입·비밀번호 재설정 화면과 같은 규칙(영문 + 숫자 포함, 8자 이상)을
 * 서버에서도 한 번 더 검사해서, 앱을 거치지 않은 요청으로 약한 비밀번호가 저장되는 것을 막는다.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 합니다.";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }

        boolean hasEnglish = false;
        boolean hasDigit = false;

        for (char c : password.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                hasEnglish = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }

        return hasEnglish && hasDigit;
    }

    // 규칙에 맞지 않으면 400 Bad Request
    public static void validate(String password) {
        if (!isValid(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MESSAGE);
        }
    }
}
