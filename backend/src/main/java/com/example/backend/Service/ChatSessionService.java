package com.example.backend.Service;

import com.example.backend.Model.DTO.chat.SessionStartResponseDTO;
import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;

    @Value("${ai.python.url:http://localhost:8000}")
    private String pythonBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    // 새로운 대화 세션을 생성하고 세션 ID + 오늘 고정질문 완료 여부 + 첫 고정질문 텍스트를 반환
    @Transactional
    public SessionStartResponseDTO startSession(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());

        Long sessionId = chatSessionRepository.save(session).getId();
        boolean fixedQuestionsDoneToday = LocalDate.now().equals(user.getLastFixedQuestionDate());

        return SessionStartResponseDTO.builder()
                .sessionId(sessionId)
                .fixedQuestionsDoneToday(fixedQuestionsDoneToday)
                .openingQuestionText(fixedQuestionsDoneToday ? null : fetchOpeningQuestionText())
                .build();
    }

    // 인사말 뒤에 이어질 첫 고정 질문 텍스트를 Python AI 서버에서 조회 (FIXED_QUESTIONS[0]이 단일 출처)
    private String fetchOpeningQuestionText() {
        try {
            Map<?, ?> response = restTemplate.getForObject(
                    pythonBaseUrl + "/opening-question", Map.class);
            Object questionText = response != null ? response.get("questionText") : null;
            return questionText != null ? questionText.toString() : null;
        } catch (Exception e) {
            log.warn("첫 고정 질문 텍스트 조회 실패: {}", e.getMessage());
            return null;
        }
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