package com.example.backend.Model.Entity.user;

import com.example.backend.Model.Entity.recall.RecallQuestion;
import com.example.backend.Model.Entity.chat.ChatSession;
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
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Integer id;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "patient_code", length = 20)
    private String patientCode;

    @Column(name = "connected_user_id")
    private Integer connectedUserId;

    // 보호자 -> 환자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_patient_id")
    private User targetPatient;

    // 환자 -> 보호자
    @OneToMany(mappedBy = "targetPatient", cascade = CascadeType.ALL)
    private List<User> protectors = new ArrayList<>();

    @Column(name = "age_group")
    private Integer ageGroup;

    @Column(name = "gender")
    private Integer gender;

    @CreatedDate // 자동으로 생성 시간 주입
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "fcm_token")
    private String fcmToken;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<ChatSession> chatSessions = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RecallQuestion> recallQuestions = new ArrayList<>();
}