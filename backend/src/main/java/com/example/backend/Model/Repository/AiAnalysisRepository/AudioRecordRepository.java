package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.chat.AudioRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AudioRecordRepository extends JpaRepository<AudioRecord, Long> {
    @Query("SELECT COALESCE(MAX(a.turnOrder), 0) FROM AudioRecord a WHERE a.chatSession.id = :sessionId")
    Integer findMaxTurnOrderByChatSessionId(@Param("sessionId") Long sessionId);
}