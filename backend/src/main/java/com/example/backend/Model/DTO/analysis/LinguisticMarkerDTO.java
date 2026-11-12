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

    // 개인 기준선(본인 과거 세션 평균) 대비 오늘의 z-score. 기준선이
    // 아직 안 쌓인 경우(과거 세션 3개 미만) null로 온다.
    private Float pronounNounRatioZScore;
    private Float nounRatioZScore;
    private Float lexicalDiversityMattrZScore;
    private Float repetitionScoreZScore;
    private Integer baselineSampleSize;
}
