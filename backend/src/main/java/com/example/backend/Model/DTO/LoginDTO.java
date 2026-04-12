package com.example.backend.Model; // 패키지 경로는 본인 설정에 맞게 확인하세요!

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDTO {
    private String email;
    private String password;
}