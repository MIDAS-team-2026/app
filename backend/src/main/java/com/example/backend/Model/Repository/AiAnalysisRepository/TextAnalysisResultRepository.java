package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.TextAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TextAnalysisResultRepository extends JpaRepository<TextAnalysisResult, Long> {
    java.util.Optional<TextAnalysisResult> findFirstByAudioRecord_IdOrderByIdDesc(Long recordId);
    Optional<TextAnalysisResult> findByAudioRecord_Id(Long recordId);
}
