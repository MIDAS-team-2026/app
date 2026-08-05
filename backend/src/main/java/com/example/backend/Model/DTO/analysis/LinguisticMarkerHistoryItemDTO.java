package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 개인 기준선 계산용으로 Python에 내려주는 과거 세션 1건의 원본 지표값.
 */
@Getter
@Setter
public class LinguisticMarkerHistoryItemDTO {
    private Long sessionId;
    private LocalDateTime analyzedAt;
    private Float pronounNounRatio;
    private Float nounRatio;
    private Float lexicalDiversityMattr;
    private Float repetitionScore;
}
