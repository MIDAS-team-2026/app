package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecordAnalysisDTO {
    private Long recordId;
    private String transcriptText;
    private SpeechAnalysisData speechAnalysis;
    private TextAnalysisData textAnalysis;

    @Getter @Setter
    public static class SpeechAnalysisData {
        private Integer pauseCount;
        private Float totalPauseDuration, avgPauseDuration, maxPauseDuration;
        private Float rmsMean, rmsStd, zcrMean, zcrStd;
        private Float spectralCentroidMean, spectralCentroidStd;
        private Float mfcc1Mean, mfcc2Mean, mfcc3Mean, mfcc4Mean, mfcc5Mean;
        private Float speechRate, articulationScore, pronunciationStability, responseLatency;
        private Integer repetitionCount, fillerCount;
    }

    @Getter @Setter
    public static class TextAnalysisData {
        private Integer wordCount, sentenceCount;
        private Float avgSentenceLength, lexicalDiversity, repeatedWordRatio;
        private Integer incompleteSentenceCount;
        private Float topicCoherenceScore;
    }
}