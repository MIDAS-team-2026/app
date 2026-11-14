package com.example.backend.Model.Entity.analysis;

import com.example.backend.Model.Entity.chat.ChatSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "risk_analysis_results")
public class RiskAnalysisResult {

    @Column(name = "evaluation_status", length = 30)
    private String evaluationStatus;

    @Column(name = "evaluation_reason", length = 500)
    private String evaluationReason;

    @Column(name = "evaluated_recall_count")
    private Integer evaluatedRecallCount;

    @Column(name = "total_recall_count")
    private Integer totalRecallCount;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_result_id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession chatSession;

    @Column(name = "speech_score")
    private Float speechScore;

    @Column(name = "text_score")
    private Float textScore;

    @Column(name = "recall_score")
    private Float recallScore;

    @Column(name = "final_risk_score")
    private Float finalRiskScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    @PreUpdate
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}
