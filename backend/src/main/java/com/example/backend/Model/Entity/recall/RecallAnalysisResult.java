package com.example.backend.Model.Entity.recall;

import com.example.backend.Model.Entity.chat.AudioRecord;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "recall_analysis_results")
public class RecallAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recall_result_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recall_question_id", nullable = false)
    private RecallQuestion recallQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "past_record_id", nullable = true)
    private AudioRecord pastRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_record_id", nullable = false)
    private AudioRecord currentRecord;

    @Column(name = "similarity_score")
    private Float similarityScore;

    @Column(name = "keyword_score")
    private Float keywordScore;

    @Column(name = "final_recall_score")
    private Float finalRecallScore;

    @Column(name = "ai_label", length = 50, nullable = true)
    private String aiLabel;

    @Column(name = "ai_confidence", nullable = true)
    private Float aiConfidence;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}