package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.AiReplyResponseDTO;
import com.example.backend.Model.DTO.analysis.FixedQuestionStatusDTO;
import com.example.backend.Model.DTO.analysis.RecallRecordLinkDTO;
import com.example.backend.Model.DTO.analysis.SessionRecordsResponseDTO;
import com.example.backend.Model.DTO.analysis.VoiceResponseDTO;
import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.AudioRecordRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.ChatSessionRepository;
import com.example.backend.Model.Repository.UserRepository;
import com.example.backend.Model.Repository.AiAnalysisRepository.RecallQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
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
    private final RecallQuestionRepository recallQuestionRepository;

    private final STTService sttService;

    private final AiProcessTriggerService aiProcessTriggerService;


    @Transactional

    public VoiceResponseDTO uploadAndSave(
            Integer userId,
            Long sessionId,
            Long recallQuestionId,
            String answerRole,
            MultipartFile file) throws IOException {

        long uploadStarted = System.nanoTime();
        String fileUrl = s3Service.uploadFile(file);
        log.info("voice_stage stage=s3_upload sessionId={} elapsed_ms={}", sessionId,
                (System.nanoTime() - uploadStarted) / 1_000_000);

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
        long sttStarted = System.nanoTime();
        STTService.SttResult sttResult = sttService.transcribeAndSave(savedRecord.getId());
        log.info("voice_stage stage=stt recordId={} elapsed_ms={}", savedRecord.getId(),
                (System.nanoTime() - sttStarted) / 1_000_000);
        Long recordId = savedRecord.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override

            public void afterCommit() {
                aiProcessTriggerService.triggerProcess(recordId, sessionId, userId);
            }

        });

        return VoiceResponseDTO.builder()
                .audioRecordId(savedRecord.getId())
                .audioFilePath(s3Service.generatePresignedGetUrl(savedRecord.getAudioFilePath()))
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
    public void saveAiReply(
            Long recordId,
            String replyText,
            String episodeId,
            String conversationTopic,
            String conversationStage,
            Long presentedRecallQuestionId
    ) {
        AudioRecord record = audioRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 녹음 기록입니다. recordId=" + recordId));
        if (presentedRecallQuestionId != null) {
            var question = recallQuestionRepository.findById(presentedRecallQuestionId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown presented recall question"));
            if (record.getUser() == null || question.getUser() == null
                    || record.getUser().getId() == null
                    || !record.getUser().getId().equals(question.getUser().getId())
                    || replyText == null || replyText.isBlank()) {
                throw new IllegalArgumentException("Invalid recall presentation");
            }
        }
        record.setAiReplyText(replyText);
        record.setPresentedRecallQuestionId(presentedRecallQuestionId);
        if (episodeId != null && !episodeId.isBlank()) {
            record.setEpisodeId(episodeId);
        }
        if (conversationTopic != null && !conversationTopic.isBlank()) {
            record.setConversationTopic(conversationTopic);
        }
        if (conversationStage != null && !conversationStage.isBlank()) {
            record.setConversationStage(conversationStage);
        }
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
    public FixedQuestionStatusDTO getFixedQuestionStatus(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        boolean doneToday = LocalDate.now().equals(user.getLastFixedQuestionDate());
        // last_fixed_question_date만 있고 onboarding 플래그가 없는 예전 유저도
        // 온보딩을 완료한 것으로 취급한다 (컬럼 추가 전 데이터 호환).
        boolean onboardingDone = user.isFixedOnboardingDone() || user.getLastFixedQuestionDate() != null;
        return new FixedQuestionStatusDTO(doneToday, onboardingDone);
    }

    @Transactional
    public void markFixedQuestionsDoneToday(Integer userId, boolean onboarding) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        user.setLastFixedQuestionDate(LocalDate.now());
        if (onboarding) {
            user.setFixedOnboardingDone(true);
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)

    public List<SessionRecordsResponseDTO> getRecordsBySession(Long sessionId) {

        return audioRecordRepository.findByChatSession_Id(sessionId).stream()

                .map(record -> new SessionRecordsResponseDTO(
                        record.getId(),
                        record.getTranscriptText(),
                        s3Service.generatePresignedGetUrl(record.getAudioFilePath()),
                        record.getAnswerRole() != null ? record.getAnswerRole().toString() : null,
                        record.getRecallQuestionId(),
                        record.getParentRecord() != null ? record.getParentRecord().getId() : null,
                        record.getAiReplyText(),
                        record.getEpisodeId(),
                        record.getConversationTopic(),
                        record.getConversationStage(),
                        record.getTurnOrder(),
                        record.getRecordedAt(),
                        record.getPresentedRecallQuestionId()
                ))

                .collect(Collectors.toList());

    }

}
