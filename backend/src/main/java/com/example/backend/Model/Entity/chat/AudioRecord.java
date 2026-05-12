package com.example.backend.Model.Entity.chat;

import com.example.backend.Model.Entity.recall.RecallAnalysisResult;
import com.example.backend.Model.Entity.analysis.SpeechAnalysisResult;
import com.example.backend.Model.Entity.analysis.TextAnalysisResult;
import com.example.backend.Model.Entity.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "audio_records")
@EntityListeners(AuditingEntityListener.class)
public class AudioRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession chatSession;

    @OneToOne(mappedBy = "audioRecord", cascade = CascadeType.ALL)
    private SpeechAnalysisResult speechAnalysisResult;

    @OneToOne(mappedBy = "audioRecord", cascade = CascadeType.ALL)
    private TextAnalysisResult textAnalysisResult;

    @Column(name = "speaker", nullable = false)
    private Integer speaker;

    @Column(name = "turn_order", nullable = false)
    private Integer turnOrder;

    @Column(name = "question_type", length = 50)
    private String questionType;

    @Column(name = "recall_question_id")
    private Long recallQuestionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_record_id")
    private AudioRecord parentRecord;

    @Column(name = "answer_role", length = 50)
    private String answerRole;

    @Column(name = "audio_file_path", length = 1000)
    private String audioFilePath;

    @Column(name = "audio_duration")
    private Float audioDuration;

    @Column(name = "transcript_text", columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "stt_confidence")
    private Float sttConfidence;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @OneToMany(mappedBy = "pastRecord")
    private List<RecallAnalysisResult> pastAnalysisBasics = new ArrayList<>();

    @OneToMany(mappedBy = "currentRecord", cascade = CascadeType.ALL)
    private List<RecallAnalysisResult> currentAnalysisResults = new ArrayList<>();
}