package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.analysis.LinguisticMarkerResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LinguisticMarkerRepository extends JpaRepository<LinguisticMarkerResult, Long> {

    Optional<LinguisticMarkerResult> findByChatSession_Id(Long sessionId);
}
