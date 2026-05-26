package com.example.backend.Model.DTO;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class SessionAnalysisSummaryResponseDTO {
    private Long sessionId;
    private Float finalRiskScore;
    private String riskLevel;
    private Float speechScore;
    private Float textScore;
    private Float recallScore;
    private LocalDateTime analyzedAt;
}