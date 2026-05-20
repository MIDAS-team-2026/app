package com.example.backend.Model.DTO;

public class LinkRequestDTO {
    private Long protectorId;
    private String patientCode;

    public Long getProtectorId() { return protectorId; }
    public void setProtectorId(Long protectorId) { this.protectorId = protectorId; }
    public String getPatientCode() { return patientCode; }
    public void setPatientCode(String patientCode) { this.patientCode = patientCode; }
}