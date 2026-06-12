package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiskAnalysisDTO {
    private Long sessionId;
    private Float speechScore;
    private Float textScore;
    private Float recallScore;
    private Float finalRiskScore;
    private String riskLevel;
}