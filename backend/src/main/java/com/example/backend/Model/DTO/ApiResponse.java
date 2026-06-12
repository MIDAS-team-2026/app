package com.example.backend.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data; // 실제 응답 데이터를 담는 필드

    // 성공 응답 (데이터 포함)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    // 성공 응답 (데이터 없음 - 위치 저장 등)
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(200, "Success", null);
    }

    // 실패 응답
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}