package com.example.backend.Service;

import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VoiceService {

    private final S3Service s3Service;
    private final AudioRecordRepository audioRecordRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;

    @Transactional
    public Long uploadAndSave(Integer userId, Long sessionId, MultipartFile file) throws IOException {
        String fileUrl = s3Service.uploadFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다."));

        Integer nextTurn = audioRecordRepository.findMaxTurnOrderByChatSessionId(sessionId) + 1;
        AudioRecord record = new AudioRecord();
        record.setUser(user);
        record.setChatSession(session);
        record.setAudioFilePath(fileUrl);
        record.setSpeaker(1); // 1: USER, 2: AI
        record.setTurnOrder(nextTurn);
        record.setRecordedAt(LocalDateTime.now());

        return audioRecordRepository.save(record).getId();
    }
}