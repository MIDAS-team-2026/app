package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupDTO {
    private String email;
    private String password;
    private String name;
    private String patientCode;
    // 필요하다면 role도 추가할 수 있지만, 기본값이 GUARDIAN이라 일단 뺐습니다.
}