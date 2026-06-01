package com.example.backend.Model.DTO.auth;

public class LoginRequestDTO {
    private String phone;
    private String password;

    // Getter, Setter
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
