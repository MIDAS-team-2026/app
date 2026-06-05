package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class RecentRiskAnalysisResponseDTO {

    private LocalDate date;
    private Long sessionId;

    private Float finalRiskScore;
    private String riskLevel;

    private Float speechScore;
    private Float textScore;
    private Float recallScore;

    private LocalDateTime analyzedAt;
}