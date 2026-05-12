package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.RiskAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskAnalysisRepository extends JpaRepository<RiskAnalysisResult, Long> {
}