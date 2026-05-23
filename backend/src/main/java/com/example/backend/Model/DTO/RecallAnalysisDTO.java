package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecallAnalysisDTO {
    private Long recallQuestionId;
    private Long pastRecordId;
    private Long currentRecordId;
    private Float similarityScore;
    private Float keywordScore;
    private Float finalRecallScore;
    private String aiLabel;
    private Float aiConfidence;
}