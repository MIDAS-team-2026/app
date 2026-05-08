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
@Table(name = "recall_questions")
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

    @OneToMany(mappedBy = "recallQuestion", cascade = CascadeType.ALL)
    private List<RecallKeyword> keywords = new ArrayList<>();

    @OneToMany(mappedBy = "recallQuestion", cascade = CascadeType.ALL)
    private List<RecallAnalysisResult> analysisResults = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}