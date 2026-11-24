package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.SpeechAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpeechAnalysisResultRepository extends JpaRepository<SpeechAnalysisResult, Long> {
    java.util.Optional<SpeechAnalysisResult> findFirstByAudioRecord_IdOrderByIdDesc(Long recordId);
    Optional<SpeechAnalysisResult> findByAudioRecord_Id(Long recordId);
}
