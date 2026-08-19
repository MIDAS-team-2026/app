package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.recall.RecallQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecallQuestionRepository extends JpaRepository<RecallQuestion, Long> {
    List<RecallQuestion> findByUser_Id(Integer userId);
    Optional<RecallQuestion> findByUser_IdAndClueId(Integer userId, String clueId);
}
