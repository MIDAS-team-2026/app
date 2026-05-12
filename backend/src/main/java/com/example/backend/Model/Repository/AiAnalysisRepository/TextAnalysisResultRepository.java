package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.TextAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextAnalysisResultRepository extends JpaRepository<TextAnalysisResult, Long> {
}