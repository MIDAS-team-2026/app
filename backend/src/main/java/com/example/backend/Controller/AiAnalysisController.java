package com.example.backend.Controller;

import com.example.backend.Model.DTO.*;
import com.example.backend.Service.AiAnalysisService;
import com.example.backend.Service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/session/{sessionId}/summary")
    public ResponseEntity<SessionAnalysisSummaryResponseDTO> getSessionSummary(@PathVariable Long sessionId) {
        return ResponseEntity.ok(aiAnalysisService.getSessionSummary(sessionId));
    }

    @PostMapping("/chat/session/{sessionId}/complete")
    public ResponseEntity<String> completeSession(@PathVariable Long sessionId) {
        chatSessionService.endSession(sessionId);
        aiAnalysisService.completeSessionAndTriggerBatch(sessionId);
        return ResponseEntity.ok("세션이 성공적으로 종료되었으며 분석 트리거가 완료되었습니다.");
    }
}