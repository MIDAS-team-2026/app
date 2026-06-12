package com.example.backend.Model.Repository.AiAnalysisRepository;

import com.example.backend.Model.Entity.chat.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}