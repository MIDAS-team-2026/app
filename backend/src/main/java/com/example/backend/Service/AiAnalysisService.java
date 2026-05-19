package com.example.backend.Service;

import com.example.backend.Model.DTO.RecallAnalysisDTO;
import com.example.backend.Model.DTO.RecordAnalysisDTO;
import com.example.backend.Model.DTO.RiskAnalysisDTO;
import com.example.backend.Model.Entity.analysis.RiskAnalysisResult;
import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.recall.RecallAnalysisResult;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AudioRecordRepository audioRecordRepository;
    private final RecallQuestionRepository recallQuestionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RecallAnalysisRepository recallAnalysisRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final NotificationService notificationService;

    @Transactional
    public void saveRecordAnalysis(RecordAnalysisDTO dto) {
        AudioRecord record = audioRecordRepository.findById(dto.getRecordId())

                .orElseThrow(() -> new IllegalArgumentException("녹음 기록을 찾을 수 없습니다."));
        record.setTranscriptText(dto.getTranscriptText());
        // 여기에 SpeechAnalysisResult, TextAnalysisResult 저장 로직 추가 가능
    }

    @Transactional
    public void saveRecallResult(RecallAnalysisDTO dto) {
        RecallAnalysisResult result = new RecallAnalysisResult();

        result.setRecallQuestion(recallQuestionRepository.findById(dto.getRecallQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다: " + dto.getRecallQuestionId())));

        // 첫 질문이라 pastRecordId가 null이거나 0으로 들어올 경우를 대비해 방어 로직 추가
        if (dto.getPastRecordId() != null && dto.getPastRecordId() > 0) {
            audioRecordRepository.findById(dto.getPastRecordId())
                    .ifPresent(result::setPastRecord);
        }

        result.setCurrentRecord(audioRecordRepository.findById(dto.getCurrentRecordId())
                .orElseThrow(() -> new IllegalArgumentException("현재 녹음 기록을 찾을 수 없습니다: " + dto.getCurrentRecordId())));

        result.setSimilarityScore(dto.getSimilarityScore());
        result.setKeywordScore(dto.getKeywordScore());
        result.setFinalRecallScore(dto.getFinalRecallScore());

        recallAnalysisRepository.save(result);
    }

    @Transactional
    public void saveFinalRiskResult(RiskAnalysisDTO dto) {
        ChatSession session = chatSessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        RiskAnalysisResult result = new RiskAnalysisResult();
        result.setChatSession(session);
        result.setSpeechScore(dto.getSpeechScore());
        result.setTextScore(dto.getTextScore());
        result.setRecallScore(dto.getRecallScore());
        result.setFinalRiskScore(dto.getFinalRiskScore());
        result.setRiskLevel(dto.getRiskLevel());

        riskAnalysisRepository.save(result);

        // --- 알림 로직 추가 ---
        // 임시 기준 점수 70점 설정
        if (dto.getFinalRiskScore() >= 70.0) {
            User patient = session.getUser();
            String title = "인지 건강 위험 알림";
            String content = String.format("[%s] 님의 오늘 분석 점수가 %.1f점으로 '위험' 단계입니다. 상세 리포트를 확인해주세요.",
                    patient.getName(), dto.getFinalRiskScore());

            notificationService.notifyAllProtectors(patient, title, content);
        }
    }
}
