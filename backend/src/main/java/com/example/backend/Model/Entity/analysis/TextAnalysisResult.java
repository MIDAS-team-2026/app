package com.example.backend.Model.Entity.analysis;

import com.example.backend.Model.Entity.chat.AudioRecord;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "text_analysis_results")
public class TextAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "text_analysis_id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private AudioRecord audioRecord;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "sentence_count")
    private Integer sentenceCount;

    @Column(name = "avg_sentence_length")
    private Float avgSentenceLength;

    @Column(name = "lexical_diversity")
    private Float lexicalDiversity;

    @Column(name = "repeated_word_ratio")
    private Float repeatedWordRatio;

    @Column(name = "incomplete_sentence_count")
    private Integer incompleteSentenceCount;

    @Column(name = "topic_coherence_score")
    private Float topicCoherenceScore;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    protected void onAnalyze() {
        this.analyzedAt = LocalDateTime.now();
    }
}