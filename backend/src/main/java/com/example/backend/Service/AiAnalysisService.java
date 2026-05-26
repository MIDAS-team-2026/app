package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.RecallAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecordAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RiskAnalysisDTO;
import com.example.backend.Model.DTO.analysis.SessionAnalysisSummaryResponseDTO;
import com.example.backend.Model.Entity.analysis.RiskAnalysisResult;
import com.example.backend.Model.Entity.analysis.SpeechAnalysisResult;
import com.example.backend.Model.Entity.analysis.TextAnalysisResult;
import com.example.backend.Model.Entity.chat.AudioRecord;
import com.example.backend.Model.Entity.chat.ChatSession;
import com.example.backend.Model.Entity.recall.RecallAnalysisResult;
import com.example.backend.Model.Entity.user.User;
import com.example.backend.Model.Repository.AiAnalysisRepository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AudioRecordRepository audioRecordRepository;
    private final RecallQuestionRepository recallQuestionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RecallAnalysisRepository recallAnalysisRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final SpeechAnalysisResultRepository speechAnalysisResultRepository;
    private final TextAnalysisResultRepository textAnalysisResultRepository;

    private final NotificationService notificationService;

    @Transactional
    public void saveRecordAnalysis(RecordAnalysisDTO dto) {
        AudioRecord record = audioRecordRepository.findById(dto.getRecordId())
                .orElseThrow(() -> new IllegalArgumentException("녹음 기록을 찾을 수 없습니다."));

        // STT 텍스트 업데이트
        if (dto.getTranscriptText() != null) {
            record.setTranscriptText(dto.getTranscriptText());
        }

        // 음성 특징(Speech) 분석 결과 저장
        if (dto.getSpeechAnalysis() != null) {
            RecordAnalysisDTO.SpeechAnalysisData speechDto = dto.getSpeechAnalysis();
            SpeechAnalysisResult speechResult = new SpeechAnalysisResult();

            speechResult.setAudioRecord(record); // FK 연결
            speechResult.setPauseCount(speechDto.getPauseCount());
            speechResult.setTotalPauseDuration(speechDto.getTotalPauseDuration());
            speechResult.setAvgPauseDuration(speechDto.getAvgPauseDuration());
            speechResult.setMaxPauseDuration(speechDto.getMaxPauseDuration());
            speechResult.setRmsMean(speechDto.getRmsMean());
            speechResult.setRmsStd(speechDto.getRmsStd());
            speechResult.setZcrMean(speechDto.getZcrMean());
            speechResult.setZcrStd(speechDto.getZcrStd());
            speechResult.setSpectralCentroidMean(speechDto.getSpectralCentroidMean());
            speechResult.setSpectralCentroidStd(speechDto.getSpectralCentroidStd());

            // === MFCC Mean 매핑 (1 ~ 13) ===
            speechResult.setMfcc1Mean(speechDto.getMfcc1Mean());
            speechResult.setMfcc2Mean(speechDto.getMfcc2Mean());
            speechResult.setMfcc3Mean(speechDto.getMfcc3Mean());
            speechResult.setMfcc4Mean(speechDto.getMfcc4Mean());
            speechResult.setMfcc5Mean(speechDto.getMfcc5Mean());
            speechResult.setMfcc6Mean(speechDto.getMfcc6Mean());
            speechResult.setMfcc7Mean(speechDto.getMfcc7Mean());
            speechResult.setMfcc8Mean(speechDto.getMfcc8Mean());
            speechResult.setMfcc9Mean(speechDto.getMfcc9Mean());
            speechResult.setMfcc10Mean(speechDto.getMfcc10Mean());
            speechResult.setMfcc11Mean(speechDto.getMfcc11Mean());
            speechResult.setMfcc12Mean(speechDto.getMfcc12Mean());
            speechResult.setMfcc13Mean(speechDto.getMfcc13Mean());

            // === MFCC Std 매핑 (1 ~ 13) ===
            speechResult.setMfcc1Std(speechDto.getMfcc1Std());
            speechResult.setMfcc2Std(speechDto.getMfcc2Std());
            speechResult.setMfcc3Std(speechDto.getMfcc3Std());
            speechResult.setMfcc4Std(speechDto.getMfcc4Std());
            speechResult.setMfcc5Std(speechDto.getMfcc5Std());
            speechResult.setMfcc6Std(speechDto.getMfcc6Std());
            speechResult.setMfcc7Std(speechDto.getMfcc7Std());
            speechResult.setMfcc8Std(speechDto.getMfcc8Std());
            speechResult.setMfcc9Std(speechDto.getMfcc9Std());
            speechResult.setMfcc10Std(speechDto.getMfcc10Std());
            speechResult.setMfcc11Std(speechDto.getMfcc11Std());
            speechResult.setMfcc12Std(speechDto.getMfcc12Std());
            speechResult.setMfcc13Std(speechDto.getMfcc13Std());

            speechResult.setSpeechRate(speechDto.getSpeechRate());
            speechResult.setArticulationScore(speechDto.getArticulationScore());
            speechResult.setPronunciationStability(speechDto.getPronunciationStability());
            speechResult.setResponseLatency(speechDto.getResponseLatency());
            speechResult.setRepetitionCount(speechDto.getRepetitionCount());
            speechResult.setFillerCount(speechDto.getFillerCount());
            speechResult.setDysarthriaSimilarityScore(speechDto.getDysarthriaSimilarityScore());
            speechResult.setDistanceFromReference(speechDto.getDistanceFromReference());
            speechResult.setSpeechAbnormalityLevel(speechDto.getSpeechAbnormalityLevel());
            speechResult.setSpeechAbnormalityScore(speechDto.getSpeechAbnormalityScore());

            speechAnalysisResultRepository.save(speechResult);
        }

        // 텍스트(Text) 분석 결과 저장
        if (dto.getTextAnalysis() != null) {
            RecordAnalysisDTO.TextAnalysisData textDto = dto.getTextAnalysis();
            TextAnalysisResult textResult = new TextAnalysisResult();

            textResult.setAudioRecord(record); // FK 연결
            textResult.setWordCount(textDto.getWordCount());
            textResult.setSentenceCount(textDto.getSentenceCount());
            textResult.setAvgSentenceLength(textDto.getAvgSentenceLength());
            textResult.setLexicalDiversity(textDto.getLexicalDiversity());
            textResult.setRepeatedWordRatio(textDto.getRepeatedWordRatio());
            textResult.setIncompleteSentenceCount(textDto.getIncompleteSentenceCount());
            textResult.setTopicCoherenceScore(textDto.getTopicCoherenceScore());
            textResult.setSlowSpeechFlag(textDto.getSlowSpeechFlag());
            textResult.setLongRecordingFlag(textDto.getLongRecordingFlag());
            textResult.setLowContentSlowSpeechFlag(textDto.getLowContentSlowSpeechFlag());

            textAnalysisResultRepository.save(textResult);
        }
    }

    @Transactional
    public void saveRecallResult(RecallAnalysisDTO dto) {
        RecallAnalysisResult result = new RecallAnalysisResult();

        result.setRecallQuestion(recallQuestionRepository.findById(dto.getRecallQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("질문을 찾을 수 없습니다: " + dto.getRecallQuestionId())));

        if (dto.getPastRecordId() != null && dto.getPastRecordId() > 0) {
            audioRecordRepository.findById(dto.getPastRecordId())
                    .ifPresent(result::setPastRecord);
        }

        result.setCurrentRecord(audioRecordRepository.findById(dto.getCurrentRecordId())
                .orElseThrow(() -> new IllegalArgumentException("현재 녹음 기록을 찾을 수 없습니다: " + dto.getCurrentRecordId())));

        result.setSimilarityScore(dto.getSimilarityScore());
        result.setKeywordScore(dto.getKeywordScore());
        result.setFinalRecallScore(dto.getFinalRecallScore());
        result.setAiLabel(dto.getAiLabel());
        result.setAiConfidence(dto.getAiConfidence());

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
        // 임시 기준 점수 70점 설정 -> 추후 결정한 risk score로 교체
        
        if (dto.getFinalRiskScore() >= 70.0) {
            User patient = session.getUser();
            String title = "인지 건강 위험 알림";
            String content = String.format("[%s] 님의 오늘 분석 점수가 %.1f점으로 '위험' 단계입니다. 상세 리포트를 확인해주세요.",
                    patient.getName(), dto.getFinalRiskScore());

            notificationService.notifyAllProtectors(patient, title, content);
        }
    }

    @Transactional(readOnly = true)
    public SessionAnalysisSummaryResponseDTO getSessionSummary(Long sessionId) {
        RiskAnalysisResult risk = riskAnalysisRepository.findByChatSession_Id(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 세션의 분석 리포트가 존재하지 않습니다."));

        return SessionAnalysisSummaryResponseDTO.builder()
                .sessionId(risk.getChatSession().getId())
                .finalRiskScore(risk.getFinalRiskScore())
                .riskLevel(risk.getRiskLevel())
                .speechScore(risk.getSpeechScore())
                .textScore(risk.getTextScore())
                .recallScore(risk.getRecallScore())
                .analyzedAt(risk.getAnalyzedAt())
                .build();
    }

    @Transactional
    public void completeSessionAndTriggerBatch(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        // 임시 주소 (파이썬 분석 주소로 추후 수정)
        String pythonServerUrl = "http://localhost:8000/api/ai/batch-analysis";

        // 파이썬에게 줄 데이터 조립
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionId", sessionId);
        requestBody.put("userId", session.getUser().getId());

        try {
            System.out.println(">> [Spring] 파이썬 AI 서버로 배치 분석 요청 시도... (세션 ID: " + sessionId + ")");

            // 실제 통신을 시도하는 부분 -> 임시 주소 채운 후 주석 해제
            // restTemplate.postForObject(pythonServerUrl, requestBody, String.class);

            System.out.println(">> [Spring] 파이썬 통신 완료 (테스트)");
        } catch (Exception e) {
            System.out.println(">> [알림] 파이썬 서버가 꺼져 있거나 포트가 달라 연결을 건너뜁니다: " + e.getMessage());
        }
    }
}