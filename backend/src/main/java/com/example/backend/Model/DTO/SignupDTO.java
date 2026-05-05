package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupDTO {
    private String email;
    private String password;
    private String name;
    private String role;
}