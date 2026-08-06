package com.example.backend.Model.Entity.analysis;

import com.example.backend.Model.Entity.chat.ChatSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 세션 단위 텍스트 언어 지표(대명사:명사 비율, 어휘 다양성, 반복성 등).
 *
 * Fraser et al. (2015) 논문 기반으로 계산되며, 현재는 위험도 점수(final_risk_score)
 * 계산에는 반영되지 않고 추적/분석 목적으로만 저장한다.
 */
@Getter
@Setter
@Entity
@Table(name = "linguistic_marker_results")
public class LinguisticMarkerResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "linguistic_result_id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession chatSession;

    @Column(name = "pronoun_noun_ratio")
    private Float pronounNounRatio;

    @Column(name = "noun_ratio")
    private Float nounRatio;

    @Column(name = "lexical_diversity_mattr")
    private Float lexicalDiversityMattr;

    @Column(name = "repetition_score")
    private Float repetitionScore;

    // 이 사용자 본인의 과거 세션 평균 대비 오늘이 얼마나 벗어났는지(z-score).
    // 과거 세션이 3개 미만이면 기준선이 불안정해서 null로 저장된다.
    @Column(name = "pronoun_noun_ratio_zscore")
    private Float pronounNounRatioZScore;

    @Column(name = "noun_ratio_zscore")
    private Float nounRatioZScore;

    @Column(name = "lexical_diversity_mattr_zscore")
    private Float lexicalDiversityMattrZScore;

    @Column(name = "repetition_score_zscore")
    private Float repetitionScoreZScore;

    @Column(name = "baseline_sample_size")
    private Integer baselineSampleSize;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    @PreUpdate
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}
