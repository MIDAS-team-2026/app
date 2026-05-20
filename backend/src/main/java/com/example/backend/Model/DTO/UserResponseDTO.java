package com.example.backend.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDTO {
    private Integer userId;
    private String phone;
    private String name;
    private String role;
    private String patientCode;
}
