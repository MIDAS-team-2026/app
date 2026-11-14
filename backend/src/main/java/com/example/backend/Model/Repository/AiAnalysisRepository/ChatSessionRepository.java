package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.chat.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM ChatSession s WHERE s.id = :id")
    java.util.Optional<ChatSession> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
