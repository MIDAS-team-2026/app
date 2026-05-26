package com.example.backend.Service;

import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;

    // 새로운 대화 세션을 생성하고 세션 ID를 반환
    @Transactional
    public Long startSession(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());

        return chatSessionRepository.save(session).getId();
    }

    @Transactional
    public void endSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        // 세션 종료 시간 기록
        session.setEndedAt(LocalDateTime.now());

        chatSessionRepository.save(session);
    }
}