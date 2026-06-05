package com.example.backend.Model.DTO.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SendCodeDTO {
    private String phone;
    /** SIGNUP: 신규 가입용 / FORGOT_PASSWORD: 비밀번호 찾기용 */
    private String purpose;
}
