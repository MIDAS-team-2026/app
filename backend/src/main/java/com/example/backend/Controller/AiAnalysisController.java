package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.RecallAnalysisDTO;
import com.example.backend.Model.DTO.RecordAnalysisDTO;
import com.example.backend.Model.DTO.RiskAnalysisDTO;
import com.example.backend.Service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/analysis")
@RequiredArgsConstructor
class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

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
}