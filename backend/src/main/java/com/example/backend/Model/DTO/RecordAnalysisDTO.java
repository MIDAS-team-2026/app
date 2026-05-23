package com.example.backend.Model.DTO;

import com.fasterxml.jackson.annotation.JsonAlias;
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
        private Float mfcc1Mean, mfcc2Mean, mfcc3Mean, mfcc4Mean, mfcc5Mean, mfcc6Mean, mfcc7Mean, mfcc8Mean, mfcc9Mean, mfcc10Mean, mfcc11Mean, mfcc12Mean, mfcc13Mean;
        private Float mfcc1Std, mfcc2Std, mfcc3Std, mfcc4Std, mfcc5Std, mfcc6Std, mfcc7Std, mfcc8Std, mfcc9Std, mfcc10Std, mfcc11Std, mfcc12Std, mfcc13Std;
        private Float speechRate, articulationScore, pronunciationStability, responseLatency;
        private Integer repetitionCount, fillerCount;

        @JsonAlias("dysarthria_similarity_score")
        private Float dysarthriaSimilarityScore;

        @JsonAlias("distance_from_reference")
        private Float distanceFromReference;

        @JsonAlias("speech_abnormality_level")
        private String speechAbnormalityLevel;

        @JsonAlias("speech_abnormality_score")
        private Integer speechAbnormalityScore;
    }

    @Getter @Setter
    public static class TextAnalysisData {
        private Integer wordCount, sentenceCount;
        private Float avgSentenceLength, lexicalDiversity, repeatedWordRatio;
        private Integer incompleteSentenceCount;
        private Float topicCoherenceScore;

        @JsonAlias("slow_speech_flag")
        private Integer slowSpeechFlag;

        @JsonAlias("long_recording_flag")
        private Integer longRecordingFlag;

        @JsonAlias("low_content_slow_speech_flag")
        private Integer lowContentSlowSpeechFlag;
    }
}