package com.example.backend.Model.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "keywords")
public class Keyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_id", nullable = false)
    private Integer id;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "word", nullable = false, length = 50)
    private String word;

}