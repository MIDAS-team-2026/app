package com.example.backend.Model.DTO.auth;

public class SignupRequestDTO {
    private String phone;
    private String password;
    private String name;
    private String role;
    private int ageGroup;
    private int gender;

    // Getter, Setter
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getAgeGroup() { return ageGroup; }
    public void setAgeGroup(int ageGroup) { this.ageGroup = ageGroup; }
    public int getGender() { return gender; }
    public void setGender(int gender) { this.gender = gender; }
}