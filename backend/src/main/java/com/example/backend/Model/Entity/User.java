package com.example.backend.Model.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
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
}