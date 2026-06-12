package com.example.backend.Model.Entity.chat;

import com.example.backend.Model.Entity.analysis.RiskAnalysisResult;
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
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL)
    private List<AudioRecord> audioRecords = new ArrayList<>();

    @OneToOne(mappedBy = "chatSession", cascade = CascadeType.ALL)
    private RiskAnalysisResult riskAnalysisResult;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * 세션 전체에 대한 요약
     * 데이터베이스의 TEXT 타입과 매핑합니다.
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @PrePersist
    protected void onStart() {
        this.startedAt = LocalDateTime.now();
    }
}