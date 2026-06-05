package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.AiReplyResponseDTO;
import com.example.backend.Model.DTO.analysis.SessionRecordsResponseDTO;
import com.example.backend.Model.DTO.analysis.VoiceResponseDTO;
import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {

    private static final String ANSWER_ROLE_INITIAL = "INITIAL";
    private static final String ANSWER_ROLE_RECALL = "RECALL";

    @Value("${ai.python.url:http://localhost:8000}")
    private String pythonBaseUrl;

    private final S3Service s3Service;
    private final AudioRecordRepository audioRecordRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public VoiceResponseDTO uploadAndSave(
            Integer userId,
            Long sessionId,
            Long recallQuestionId,
            String answerRole,
            MultipartFile file) throws IOException {

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
        record.setRecallQuestionId(recallQuestionId);
        record.setAnswerRole(answerRole);

        // RECALL 답변이면 해당 질문의 INITIAL 녹음을 부모로 연결
        if (recallQuestionId != null && ANSWER_ROLE_RECALL.equalsIgnoreCase(answerRole)) {
            audioRecordRepository
                    .findFirstByUser_IdAndRecallQuestionIdAndAnswerRoleOrderByRecordedAtDesc(
                            userId, recallQuestionId, ANSWER_ROLE_INITIAL)
                    .ifPresent(record::setParentRecord);
        }

        AudioRecord savedRecord = audioRecordRepository.save(record);

        // Python AI에 답변 생성 요청 (비동기 — 업로드 응답에 영향 없음)
        triggerAiReply(savedRecord.getId(), sessionId, userId);

        return VoiceResponseDTO.builder()
                .audioRecordId(savedRecord.getId())
                .audioFilePath(savedRecord.getAudioFilePath())
                .turnOrder(savedRecord.getTurnOrder())
                .recordedAt(savedRecord.getRecordedAt())
                .build();
    }

    /** Python FastAPI 서버에 AI 답변 생성 요청. 실패해도 업로드 흐름에 영향 없음. */
    @Async
    public void triggerAiReply(Long recordId, Long sessionId, Integer userId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("recordId", recordId);
            body.put("sessionId", sessionId);
            body.put("userId", userId);
            restTemplate.postForEntity(pythonBaseUrl + "/process", body, String.class);
        } catch (Exception e) {
            log.warn("Python AI 답변 생성 요청 실패 recordId={}: {}", recordId, e.getMessage());
        }
    }

    @Transactional
    public void updateTranscript(Long recordId, String transcriptText) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        record.setTranscriptText(transcriptText);
        audioRecordRepository.save(record);
    }

    /** Python AI가 답변 생성 완료 후 호출 — recordId에 해당하는 레코드에 replyText 저장. */
    @Transactional
    public void saveAiReply(Long recordId, String replyText) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        record.setAiReplyText(replyText);
        audioRecordRepository.save(record);
    }

    /** 앱 폴링용 — replyText가 null이면 아직 생성 중. */
    @Transactional(readOnly = true)
    public AiReplyResponseDTO getAiReply(Long recordId) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        return new AiReplyResponseDTO(record.getId(), record.getAiReplyText());
    }

    @Transactional(readOnly = true)
    public List<SessionRecordsResponseDTO> getRecordsBySession(Long sessionId) {
        return audioRecordRepository.findByChatSession_Id(sessionId).stream()
                .map(record -> new SessionRecordsResponseDTO(
                        record.getId(),
                        record.getTranscriptText(),
                        record.getAudioFilePath(),
                        record.getAnswerRole() != null ? record.getAnswerRole().toString() : null,
                        record.getRecallQuestionId()
                ))
                .collect(Collectors.toList());
    }
}
