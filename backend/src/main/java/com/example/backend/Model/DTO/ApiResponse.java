package com.example.backend.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse {
    private int code;
    private String message;

    public static ApiResponse success() {
        return new ApiResponse(200, "Success");
    }

    public static ApiResponse fail() {
        return new ApiResponse(500, "Fail");
    }
}