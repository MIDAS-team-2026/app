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

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}