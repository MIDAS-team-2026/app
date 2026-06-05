package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.SessionRecordsResponseDTO;
import com.example.backend.Model.DTO.analysis.VoiceResponseDTO;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoiceService {

    private static final String ANSWER_ROLE_INITIAL = "INITIAL";
    private static final String ANSWER_ROLE_RECALL = "RECALL";

    private final S3Service s3Service;
    private final AudioRecordRepository audioRecordRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final STTService sttService;

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
        STTService.SttResult sttResult = sttService.transcribeAndSave(savedRecord.getId());

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
