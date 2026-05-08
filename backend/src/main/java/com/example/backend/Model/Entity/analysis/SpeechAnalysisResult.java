package com.example.backend.Model.Entity.analysis;

import com.example.backend.Model.Entity.chat.AudioRecord;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "speech_analysis_results")
public class SpeechAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "speech_analysis_id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private AudioRecord audioRecord;

    @Column(name = "pause_count", nullable = false)
    private Integer pauseCount;

    @Column(name = "total_pause_duration", nullable = false)
    private Float totalPauseDuration;

    @Column(name = "avg_pause_duration", nullable = false)
    private Float avgPauseDuration;

    @Column(name = "max_pause_duration", nullable = false)
    private Float maxPauseDuration;

    @Column(name = "rms_mean", nullable = false)
    private Float rmsMean;

    @Column(name = "rms_std", nullable = false)
    private Float rmsStd;

    @Column(name = "zcr_mean", nullable = false)
    private Float zcrMean;

    @Column(name = "zcr_std", nullable = false)
    private Float zcrStd;

    @Column(name = "spectral_centroid_mean", nullable = false)
    private Float spectralCentroidMean;

    @Column(name = "spectral_centroid_std", nullable = false)
    private Float spectralCentroidStd;

    @Column(name = "mfcc_1_mean", nullable = false)
    private Float mfcc1Mean;

    @Column(name = "mfcc_2_mean", nullable = false)
    private Float mfcc2Mean;

    @Column(name = "mfcc_3_mean", nullable = false)
    private Float mfcc3Mean;

    @Column(name = "mfcc_4_mean", nullable = false)
    private Float mfcc4Mean;

    @Column(name = "mfcc_5_mean", nullable = false)
    private Float mfcc5Mean;

    @Column(name = "speech_rate")
    private Float speechRate;

    @Column(name = "articulation_score")
    private Float articulationScore;

    @Column(name = "pronunciation_stability")
    private Float pronunciationStability;

    @Column(name = "response_latency")
    private Float responseLatency;

    @Column(name = "repetition_count")
    private Integer repetitionCount;

    @Column(name = "filler_count")
    private Integer fillerCount;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}