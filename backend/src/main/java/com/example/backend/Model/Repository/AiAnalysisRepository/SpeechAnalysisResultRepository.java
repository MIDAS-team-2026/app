package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.SpeechAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeechAnalysisResultRepository extends JpaRepository<SpeechAnalysisResult, Long> {
}