package com.example.backend.Model.Entity.recall;

import com.example.backend.Model.Entity.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "recall_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recall_question_user_clue",
                columnNames = {"user_id", "clue_id"}
        )
)
public class RecallQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recall_question_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expected_answer", columnDefinition = "TEXT", nullable = true)
    private String expectedAnswer;

    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "clue_id", length = 200)
    private String clueId;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "answer_type", length = 30)
    private String answerType;

    @OneToMany(mappedBy = "recallQuestion", cascade = CascadeType.ALL)
    private List<RecallKeyword> keywords = new ArrayList<>();

    @OneToMany(mappedBy = "recallQuestion", cascade = CascadeType.ALL)
    private List<RecallAnalysisResult> analysisResults = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
