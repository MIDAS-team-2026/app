package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.RiskAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskAnalysisRepository extends JpaRepository<RiskAnalysisResult, Long> {
    Optional<RiskAnalysisResult> findByChatSession_Id(Long sessionId);
}