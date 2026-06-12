package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.recall.RecallAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecallAnalysisRepository extends JpaRepository<RecallAnalysisResult, Long> {
}