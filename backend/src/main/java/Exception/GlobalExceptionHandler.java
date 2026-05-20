package com.example.backend.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 잘못된 값(IllegalArgumentException)이 들어왔을 때 -> 400 에러로 변환
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400);
        response.put("message", e.getMessage());
        response.put("data", null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 2. 그 외의 예상치 못한 서버 에러(Exception)가 터졌을 때 -> 500 에러로 변환
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 500);
        response.put("message", "서버 내부 오류가 발생했습니다: " + e.getMessage());
        response.put("data", null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}