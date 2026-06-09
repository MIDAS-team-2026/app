package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.AiReplyResponseDTO;
import com.example.backend.Model.DTO.analysis.RecallRecordLinkDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {
    private static final String ANSWER_ROLE_RECALL = "RECALL";
    private final S3Service s3Service;
    private final AudioRecordRepository audioRecordRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;

    private final STTService sttService;

    private final AiProcessTriggerService aiProcessTriggerService;


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
        record.setRecallQuestionId(null);
        record.setAnswerRole(null);
        record.setParentRecord(null);

        AudioRecord savedRecord = audioRecordRepository.save(record);
        STTService.SttResult sttResult = sttService.transcribeAndSave(savedRecord.getId());
        Long recordId = savedRecord.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override

            public void afterCommit() {
                aiProcessTriggerService.triggerProcess(recordId, sessionId, userId);
            }

        });

        return VoiceResponseDTO.builder()
                .audioRecordId(savedRecord.getId())
                .audioFilePath(savedRecord.getAudioFilePath())
                .transcriptText(sttResult.getTranscriptText())
                .turnOrder(savedRecord.getTurnOrder())
                .recordedAt(savedRecord.getRecordedAt())
                .build();
    }
    @Transactional
    public void updateTranscript(Long recordId, String transcriptText) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        record.setTranscriptText(transcriptText);
        audioRecordRepository.save(record);
    }

    @Transactional
    public String transcribeRecord(Long recordId) {
        return sttService.transcribeAndSave(recordId).getTranscriptText();
    }

    @Transactional
    public void saveAiReply(Long recordId, String replyText) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        record.setAiReplyText(replyText);
        audioRecordRepository.save(record);
        log.info("AI reply 저장 완료 recordId={}", recordId);
    }

    @Transactional
    public void linkRecallQuestion(Long recordId, Long recallQuestionId, String answerRole) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        record.setRecallQuestionId(recallQuestionId);
        if (answerRole != null && !answerRole.isBlank()) {
            record.setAnswerRole(answerRole);
        }
        audioRecordRepository.save(record);
        log.info("회상 질문 연결 완료 recordId={} recallQuestionId={}", recordId, recallQuestionId);
    }



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
                        record.getRecallQuestionId(),
                        record.getParentRecord() != null ? record.getParentRecord().getId() : null
                ))

                .collect(Collectors.toList());

    }

}

