package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.analysis.RecallAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecordAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecentRiskAnalysisResponseDTO;
import com.example.backend.Model.DTO.analysis.RiskAnalysisDTO;
import com.example.backend.Model.DTO.analysis.SessionAnalysisSummaryResponseDTO;
import com.example.backend.Service.AiAnalysisService;
import com.example.backend.Service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/analysis")
@RequiredArgsConstructor
class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final ChatSessionService chatSessionService;

    // 발화별 분석 결과 수신 (음성 특징, 텍스트 특징)
    @PostMapping("/record")
    public ResponseEntity<ApiResponse<Void>> receiveRecordResult(@RequestBody RecordAnalysisDTO dto) {
        aiAnalysisService.saveRecordAnalysis(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 회상 분석 결과 수신
    @PostMapping("/recall")
    public ResponseEntity<ApiResponse<Void>> receiveRecallResult(@RequestBody RecallAnalysisDTO dto) {
        aiAnalysisService.saveRecallResult(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 최종 위험도 결과 수신
    @PostMapping("/risk")
    public ResponseEntity<ApiResponse<Void>> receiveRiskResult(@RequestBody RiskAnalysisDTO dto) {
        aiAnalysisService.saveFinalRiskResult(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 세션별 최종 분석 요약 조회
    @GetMapping("/session/{sessionId}/summary")
    public ResponseEntity<SessionAnalysisSummaryResponseDTO> getSessionSummary(@PathVariable Long sessionId) {
        return ResponseEntity.ok(aiAnalysisService.getSessionSummary(sessionId));
    }

    // 최근 7일 분석 결과 조회
    @GetMapping("/recent7days/{userId}")
    public ResponseEntity<ApiResponse<List<RecentRiskAnalysisResponseDTO>>> getRecent7DaysAnalysis(
            @PathVariable Integer userId) {

        List<RecentRiskAnalysisResponseDTO> result = aiAnalysisService.getRecent7DaysAnalysis(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 세션 종료 및 Python 배치 분석 트리거
    @PostMapping("/chat/session/{sessionId}/complete")
    public ResponseEntity<String> completeSession(@PathVariable Long sessionId) {
        chatSessionService.endSession(sessionId);
        aiAnalysisService.completeSessionAndTriggerBatch(sessionId);
        return ResponseEntity.ok("세션이 성공적으로 종료되었으며 분석 트리거가 완료되었습니다.");
    }
}