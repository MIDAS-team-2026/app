package com.example.backend.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400);
        response.put("message", e.getMessage());
        response.put("data", null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 500);
        response.put("message", "서버 내부 오류가 발생했습니다: " + e.getMessage());
        response.put("data", null);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
