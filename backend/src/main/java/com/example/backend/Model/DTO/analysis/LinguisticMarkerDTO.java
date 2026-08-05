package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinguisticMarkerDTO {
    private Long sessionId;
    private Float pronounNounRatio;
    private Float nounRatio;
    private Float lexicalDiversityMattr;
    private Float repetitionScore;
}
