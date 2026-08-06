package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 과거 세션 1건의 언어 지표값. Python이 개인 기준선(z-score) 계산할 때는
 * 원본값(pronounNounRatio 등)만 쓰고, 앱이 상세 화면을 그릴 때는 z-score와
 * baselineSampleSize까지 함께 쓴다 — 같은 응답을 두 용도로 재사용한다.
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
    private Float pronounNounRatioZScore;
    private Float nounRatioZScore;
    private Float lexicalDiversityMattrZScore;
    private Float repetitionScoreZScore;
    private Integer baselineSampleSize;
}
