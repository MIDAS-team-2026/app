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

    @Column(name = "pause_count", nullable = true)
    private Integer pauseCount;

    @Column(name = "total_pause_duration", nullable = true)
    private Float totalPauseDuration;

    @Column(name = "avg_pause_duration", nullable = true)
    private Float avgPauseDuration;

    @Column(name = "max_pause_duration", nullable = true)
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

    // === MFCC Mean (1 ~ 13) ===
    @Column(name = "mfcc_1_mean", nullable = false) private Float mfcc1Mean;
    @Column(name = "mfcc_2_mean", nullable = false) private Float mfcc2Mean;
    @Column(name = "mfcc_3_mean", nullable = false) private Float mfcc3Mean;
    @Column(name = "mfcc_4_mean", nullable = false) private Float mfcc4Mean;
    @Column(name = "mfcc_5_mean", nullable = false) private Float mfcc5Mean;
    @Column(name = "mfcc_6_mean", nullable = false) private Float mfcc6Mean;
    @Column(name = "mfcc_7_mean", nullable = false) private Float mfcc7Mean;
    @Column(name = "mfcc_8_mean", nullable = false) private Float mfcc8Mean;
    @Column(name = "mfcc_9_mean", nullable = false) private Float mfcc9Mean;
    @Column(name = "mfcc_10_mean", nullable = false) private Float mfcc10Mean;
    @Column(name = "mfcc_11_mean", nullable = false) private Float mfcc11Mean;
    @Column(name = "mfcc_12_mean", nullable = false) private Float mfcc12Mean;
    @Column(name = "mfcc_13_mean", nullable = false) private Float mfcc13Mean;

    // === MFCC Std (1 ~ 13) ===
    @Column(name = "mfcc_1_std", nullable = false) private Float mfcc1Std;
    @Column(name = "mfcc_2_std", nullable = false) private Float mfcc2Std;
    @Column(name = "mfcc_3_std", nullable = false) private Float mfcc3Std;
    @Column(name = "mfcc_4_std", nullable = false) private Float mfcc4Std;
    @Column(name = "mfcc_5_std", nullable = false) private Float mfcc5Std;
    @Column(name = "mfcc_6_std", nullable = false) private Float mfcc6Std;
    @Column(name = "mfcc_7_std", nullable = false) private Float mfcc7Std;
    @Column(name = "mfcc_8_std", nullable = false) private Float mfcc8Std;
    @Column(name = "mfcc_9_std", nullable = false) private Float mfcc9Std;
    @Column(name = "mfcc_10_std", nullable = false) private Float mfcc10Std;
    @Column(name = "mfcc_11_std", nullable = false) private Float mfcc11Std;
    @Column(name = "mfcc_12_std", nullable = false) private Float mfcc12Std;
    @Column(name = "mfcc_13_std", nullable = false) private Float mfcc13Std;

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

    /** Python speech_abnormality_scoring: 참고군 대비 유사도 (0~1) */
    @Column(name = "dysarthria_similarity_score")
    private Float dysarthriaSimilarityScore;

    /** Python speech_abnormality_scoring: 참고군과의 z-score 거리 */
    @Column(name = "distance_from_reference")
    private Float distanceFromReference;

    /** Python speech_abnormality_scoring: Low / Medium / High */
    @Column(name = "speech_abnormality_level", length = 20)
    private String speechAbnormalityLevel;

    /** Python speech_abnormality_scoring: 위험도 반영용 발화 이상 점수 (0, 7, 15) */
    @Column(name = "speech_abnormality_score")
    private Integer speechAbnormalityScore;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}