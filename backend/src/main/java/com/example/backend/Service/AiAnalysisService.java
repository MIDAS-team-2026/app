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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final AudioRecordRepository audioRecordRepository;
    private final RecallQuestionRepository recallQuestionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RecallAnalysisRepository recallAnalysisRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final NotificationService notificationService; // 알림 서비스 주입

    // 녹음 분석 결과 저장 (텍스트 변환 등)
    @Transactional
    public void saveRecordAnalysis(RecordAnalysisDTO dto) {
        AudioRecord record = audioRecordRepository.findById(dto.getRecordId())
                .orElseThrow(() -> new IllegalArgumentException("녹음 기록을 찾을 수 없습니다."));

        record.setTranscriptText(dto.getTranscriptText());
        log.info("녹음 텍스트 변환 결과 저장 완료: recordId={}", dto.getRecordId());
    }

    // 회상 질문 분석 결과 저장
    @Transactional
    public void saveRecallResult(RecallAnalysisDTO dto) {
        RecallAnalysisResult result = new RecallAnalysisResult();
        result.setRecallQuestion(recallQuestionRepository.findById(dto.getRecallQuestionId()).orElseThrow());
        result.setPastRecord(audioRecordRepository.findById(dto.getPastRecordId()).orElseThrow());
        result.setCurrentRecord(audioRecordRepository.findById(dto.getCurrentRecordId()).orElseThrow());

        result.setSimilarityScore(dto.getSimilarityScore());
        result.setKeywordScore(dto.getKeywordScore());
        result.setFinalRecallScore(dto.getFinalRecallScore());

        recallAnalysisRepository.save(result);
        log.info("회상 질문 분석 저장 완료: questionId={}", dto.getRecallQuestionId());
    }

    // 최종 위험도 분석 결과 저장 및 알림 발송
    @Transactional
    public void saveFinalRiskResult(RiskAnalysisDTO dto) {
        ChatSession session = chatSessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        // 분석 결과 엔티티 생성 및 저장
        RiskAnalysisResult result = new RiskAnalysisResult();
        result.setChatSession(session);
        result.setSpeechScore(dto.getSpeechScore());
        result.setTextScore(dto.getTextScore());
        result.setRecallScore(dto.getRecallScore());
        result.setFinalRiskScore(dto.getFinalRiskScore());
        result.setRiskLevel(dto.getRiskLevel());

        riskAnalysisRepository.save(result);
        log.info("최종 위험도 분석 결과 저장 완료: sessionId={}, score={}", dto.getSessionId(), dto.getFinalRiskScore());

        // --- 임시 알림 로직 (70점 이상 시 보호자 전원에게 FCM 발송) ---
        if (dto.getFinalRiskScore() >= 70.0) {
            User patient = session.getUser();
            String title = "인지 건강 위험 신호 감지";
            String content = String.format("[%s] 님의 오늘 인지 건강 분석 점수가 %.1f점(위험)입니다. 앱에서 상세 보고서를 확인하세요.",
                    patient.getName(), dto.getFinalRiskScore());

            notificationService.notifyAllProtectors(patient, title, content);
        }
    }
}