package com.example.backend.Model.Entity.user;

import com.example.backend.Model.Entity.recall.RecallQuestion;
import com.example.backend.Model.Entity.chat.ChatSession;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
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

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "patient_code", length = 20)
    private String patientCode;

    // 보호자 -> 연결된 환자 목록 (N:M)
    @ManyToMany
    @JoinTable(
        name = "guardian_patient",
        joinColumns = @JoinColumn(name = "guardian_id"),
        inverseJoinColumns = @JoinColumn(name = "patient_id")
    )
    private List<User> patients = new ArrayList<>();

    // 환자 -> 연결된 보호자 목록 (N:M 역방향)
    @ManyToMany(mappedBy = "patients")
    private List<User> guardians = new ArrayList<>();

    @Column(name = "age_group")
    private Integer ageGroup;

    @Column(name = "gender")
    private Integer gender;

    @CreatedDate // 자동으로 생성 시간 주입
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 고정질문(초기 5문항)을 마지막으로 완료한 날짜 — 하루 1회 노출 제한에 사용
    @Column(name = "last_fixed_question_date")
    private LocalDate lastFixedQuestionDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<ChatSession> chatSessions = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<RecallQuestion> recallQuestions = new ArrayList<>();
}