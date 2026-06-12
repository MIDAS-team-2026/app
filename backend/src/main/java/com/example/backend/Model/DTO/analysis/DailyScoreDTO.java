package com.example.backend.Model.DTO.analysis;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class DailyScoreDTO {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    private Float finalRiskScore;
    private String riskLevel;
    private Float speechScore;
    private Float textScore;
    private Float recallScore;
    private int sessionCount;
}
