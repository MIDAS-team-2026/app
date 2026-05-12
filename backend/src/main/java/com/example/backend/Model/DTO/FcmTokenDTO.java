package com.example.backend.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenDTO {
    private Integer userId; // 보호자의 사용자 ID
    private String token;  // 프론트에서 발급받은 FCM 토큰 값
}