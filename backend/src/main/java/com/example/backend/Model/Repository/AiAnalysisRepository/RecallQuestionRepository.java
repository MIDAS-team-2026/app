package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.recall.RecallQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecallQuestionRepository extends JpaRepository<RecallQuestion, Long> {
}