package com.example.backend.Model.Entity.recall;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recall_keywords")
public class RecallKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recall_question_id", nullable = false)
    private RecallQuestion recallQuestion;

    @Column(name = "keyword_text", nullable = false, length = 100)
    private String keywordText;
}