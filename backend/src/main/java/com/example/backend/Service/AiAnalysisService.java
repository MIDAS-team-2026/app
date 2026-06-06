package com.example.backend.Service;

import com.example.backend.Model.DTO.analysis.DailyScoreDTO;
import com.example.backend.Model.DTO.analysis.RecallAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecordAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecentRiskAnalysisResponseDTO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService {

    private final AudioRecordRepository audioRecordRepository;
    private final RecallQuestionRepository recallQuestionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final RecallAnalysisRepository recallAnalysisRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final SpeechAnalysisResultRepository speechAnalysisResultRepository;
    private final TextAnalysisResultRepository textAnalysisResultRepository;

    private final NotificationService notificationService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.python.url:http://localhost:8000}")
    private String pythonBaseUrl;

    @Transactional
    public void saveRecordAnalysis(RecordAnalysisDTO dto) {
        AudioRecord record = audioRecordRepository.findById(dto.getRecordId())
                .orElseThrow(() -> new IllegalArgumentException("녹음 기록을 찾을 수 없습니다."));

        if (dto.getTranscriptText() != null) {
            record.setTranscriptText(dto.getTranscriptText());
        }

        if (dto.getSpeechAnalysis() != null) {
            RecordAnalysisDTO.SpeechAnalysisData speechDto = dto.getSpeechAnalysis();
            SpeechAnalysisResult speechResult = new SpeechAnalysisResult();

            speechResult.setAudioRecord(record);
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

        if (dto.getTextAnalysis() != null) {
            RecordAnalysisDTO.TextAnalysisData textDto = dto.getTextAnalysis();
            TextAnalysisResult textResult = new TextAnalysisResult();

            textResult.setAudioRecord(record);
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
        log.info("회상 분석 결과 저장 완료 recallQuestionId={} currentRecordId={}",
                dto.getRecallQuestionId(), dto.getCurrentRecordId());
    }

    @Transactional
    public void saveFinalRiskResult(RiskAnalysisDTO dto) {
        ChatSession session = chatSessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        RiskAnalysisResult result = riskAnalysisRepository.findByChatSession_Id(dto.getSessionId())
                .orElseGet(RiskAnalysisResult::new);
        result.setChatSession(session);
        result.setSpeechScore(dto.getSpeechScore());
        result.setTextScore(dto.getTextScore());
        result.setRecallScore(dto.getRecallScore());
        result.setFinalRiskScore(dto.getFinalRiskScore());
        result.setRiskLevel(dto.getRiskLevel());

        riskAnalysisRepository.save(result);

        if (dto.getFinalRiskScore() >= 70.0) {
            User patient = session.getUser();
            String title = "인지 건강 위험 알림";
            String content = String.format("[%s] 님의 오늘 분석 점수가 %.1f점으로 '위험' 단계입니다. 상세 리포트를 확인해주세요.",
                    patient.getName(), dto.getFinalRiskScore());

            notificationService.notifyAllProtectors(patient, title, content);
        }
    }

    @Transactional(readOnly = true)
    public int getStreakDays(Integer userId) {
        int streak = 0;
        LocalDate date = LocalDate.now();
        while (true) {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end   = start.plusDays(1);
            List<RiskAnalysisResult> dayResults = riskAnalysisRepository
                    .findByChatSession_User_IdAndAnalyzedAtBetween(userId, start, end);
            if (dayResults.isEmpty()) break;
            streak++;
            date = date.minusDays(1);
        }
        return streak;
    }

    @Transactional(readOnly = true)
    public List<DailyScoreDTO> getWeeklyScores(Integer userId) {
        LocalDate today = LocalDate.now();
        List<DailyScoreDTO> result = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end   = start.plusDays(1);

            List<RiskAnalysisResult> dayResults = riskAnalysisRepository
                    .findByChatSession_User_IdAndAnalyzedAtBetween(userId, start, end);

            if (dayResults.isEmpty()) {
                result.add(new DailyScoreDTO(date, null, null, null, null, null, 0));
            } else {
                float avgRisk   = avg(dayResults, r -> r.getFinalRiskScore());
                float avgSpeech = avg(dayResults, r -> r.getSpeechScore());
                float avgText   = avg(dayResults, r -> r.getTextScore());
                float avgRecall = avg(dayResults, r -> r.getRecallScore());
                result.add(new DailyScoreDTO(
                        date, avgRisk, resolveRiskLevel(avgRisk),
                        avgSpeech, avgText, avgRecall, dayResults.size()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DailyScoreDTO getDailyScore(Integer userId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        List<RiskAnalysisResult> dayResults = riskAnalysisRepository
                .findByChatSession_User_IdAndAnalyzedAtBetween(userId, start, end);

        if (dayResults.isEmpty())
            throw new IllegalArgumentException("해당 날짜의 분석 결과가 없습니다.");

        float avgRisk   = avg(dayResults, r -> r.getFinalRiskScore());
        float avgSpeech = avg(dayResults, r -> r.getSpeechScore());
        float avgText   = avg(dayResults, r -> r.getTextScore());
        float avgRecall = avg(dayResults, r -> r.getRecallScore());
        return new DailyScoreDTO(
                date, avgRisk, resolveRiskLevel(avgRisk),
                avgSpeech, avgText, avgRecall, dayResults.size());
    }

    @Transactional(readOnly = true)
    public SessionAnalysisSummaryResponseDTO getTodayAverageSummary(Integer userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = startOfDay.plusDays(1);

        List<RiskAnalysisResult> todayResults = riskAnalysisRepository
                .findByChatSession_User_IdAndAnalyzedAtBetween(userId, startOfDay, endOfDay);

        if (todayResults.isEmpty())
            throw new IllegalArgumentException("오늘 분석 결과가 없습니다.");

        float avgRisk    = avg(todayResults, r -> r.getFinalRiskScore());
        float avgSpeech  = avg(todayResults, r -> r.getSpeechScore());
        float avgText    = avg(todayResults, r -> r.getTextScore());
        float avgRecall  = avg(todayResults, r -> r.getRecallScore());
        String riskLevel = resolveRiskLevel(avgRisk);

        return SessionAnalysisSummaryResponseDTO.builder()
                .sessionId(null)
                .finalRiskScore(avgRisk)
                .riskLevel(riskLevel)
                .speechScore(avgSpeech)
                .textScore(avgText)
                .recallScore(avgRecall)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private float avg(List<RiskAnalysisResult> list,
                      java.util.function.Function<RiskAnalysisResult, Float> getter) {
        return (float) list.stream()
                .mapToDouble(r -> getter.apply(r) != null ? getter.apply(r) : 0f)
                .average()
                .orElse(0.0);
    }

    private String resolveRiskLevel(float score) {
        if (score < 30.0f) return "LOW";
        if (score < 60.0f) return "MEDIUM";
        return "HIGH";
    }

    @Transactional(readOnly = true)
    public SessionAnalysisSummaryResponseDTO getLatestSummaryByUser(Integer userId) {
        RiskAnalysisResult risk = riskAnalysisRepository
                .findTopByChatSession_User_IdOrderByAnalyzedAtDesc(userId)
                .orElseThrow(() -> new IllegalArgumentException("분석 결과가 없습니다."));
        return toSummaryDTO(risk);
    }

    @Transactional(readOnly = true)
    public SessionAnalysisSummaryResponseDTO getSessionSummary(Long sessionId) {
        RiskAnalysisResult risk = riskAnalysisRepository.findByChatSession_Id(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 세션의 분석 리포트가 존재하지 않습니다."));
        return toSummaryDTO(risk);
    }

    private SessionAnalysisSummaryResponseDTO toSummaryDTO(RiskAnalysisResult risk) {
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

    @Transactional(readOnly = true)
    public List<RecentRiskAnalysisResponseDTO> getRecent7DaysAnalysis(Integer userId) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);

        List<RiskAnalysisResult> results =
                riskAnalysisRepository.findByChatSession_User_IdAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                        userId,
                        startDate
                );

        return results.stream()
                .map(risk -> RecentRiskAnalysisResponseDTO.builder()
                        .date(risk.getAnalyzedAt().toLocalDate())
                        .sessionId(risk.getChatSession().getId())
                        .finalRiskScore(risk.getFinalRiskScore())
                        .riskLevel(risk.getRiskLevel())
                        .speechScore(risk.getSpeechScore())
                        .textScore(risk.getTextScore())
                        .recallScore(risk.getRecallScore())
                        .analyzedAt(risk.getAnalyzedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void completeSessionAndTriggerBatch(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다."));

        String pythonServerUrl = pythonBaseUrl + "/api/ai/batch-analysis";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionId", sessionId);
        requestBody.put("userId", session.getUser().getId());

        try {
            System.out.println(">> [Spring] 파이썬 AI 서버로 배치 분석 요청 시도... (세션 ID: " + sessionId + ")");

            restTemplate.postForObject(pythonServerUrl, requestBody, String.class);

            System.out.println(">> [Spring] 파이썬 통신 완료 (요청 전송 성공)");
        } catch (Exception e) {
            System.out.println(">> [Spring] 통신 실패: " + e.getMessage());
        }
    }
}
