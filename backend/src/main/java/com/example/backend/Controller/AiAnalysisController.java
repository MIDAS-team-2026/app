package com.example.backend.Controller;

import com.example.backend.Model.DTO.*;
import com.example.backend.Model.DTO.analysis.DailyScoreDTO;
import com.example.backend.Model.DTO.analysis.LinguisticMarkerDTO;
import com.example.backend.Model.DTO.analysis.RecallAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecordAnalysisDTO;
import com.example.backend.Model.DTO.analysis.RecentRiskAnalysisResponseDTO;
import com.example.backend.Model.DTO.analysis.RiskAnalysisDTO;
import com.example.backend.Model.DTO.analysis.SessionAnalysisSummaryResponseDTO;
import com.example.backend.Service.AiAnalysisService;
import com.example.backend.Service.ChatSessionService;
import com.example.backend.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ai/analysis")
@RequiredArgsConstructor
class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final ChatSessionService chatSessionService;
    private final UserService userService;

    // 발화별 분석 결과 수신
    // Python/FastAPI 연동용이므로 현재는 인증 적용하지 않음
    @PostMapping("/record")
    public ResponseEntity<ApiResponse<Void>> receiveRecordResult(@RequestBody RecordAnalysisDTO dto) {
        aiAnalysisService.saveRecordAnalysis(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 회상 분석 결과 수신
    // Python/FastAPI 연동용이므로 현재는 인증 적용하지 않음
    @PostMapping("/recall")
    public ResponseEntity<ApiResponse<Void>> receiveRecallResult(@RequestBody RecallAnalysisDTO dto) {
        aiAnalysisService.saveRecallResult(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 최종 위험도 결과 수신
    // Python/FastAPI 연동용이므로 현재는 인증 적용하지 않음
    @PostMapping("/risk")
    public ResponseEntity<ApiResponse<Void>> receiveRiskResult(@RequestBody RiskAnalysisDTO dto) {
        aiAnalysisService.saveFinalRiskResult(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 세션 단위 텍스트 언어 지표(대명사:명사 비율 등) 수신 — 위험도 점수에는 미반영, 추적용
    // Python/FastAPI 연동용이므로 현재는 인증 적용하지 않음
    @PostMapping("/linguistic-markers")
    public ResponseEntity<ApiResponse<Void>> receiveLinguisticMarkers(@RequestBody LinguisticMarkerDTO dto) {
        aiAnalysisService.saveLinguisticMarkers(dto);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 세션별 최종 분석 요약 조회
    // TODO: sessionId 기반으로 세션 소유자 확인 로직을 추가하면 더 안전함
    @GetMapping("/session/{sessionId}/summary")
    public ResponseEntity<SessionAnalysisSummaryResponseDTO> getSessionSummary(@PathVariable Long sessionId) {
        return ResponseEntity.ok(aiAnalysisService.getSessionSummary(sessionId));
    }

    // 사용자 최신 분석 결과 조회
    @GetMapping("/user/{userId}/latest")
    public ResponseEntity<ApiResponse<SessionAnalysisSummaryResponseDTO>> getLatestSummary(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

        try {
            return ResponseEntity.ok(ApiResponse.success(aiAnalysisService.getLatestSummaryByUser(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(404, e.getMessage()));
        }
    }

    // 사용자 오늘 평균 분석 결과 조회
    @GetMapping("/user/{userId}/today")
    public ResponseEntity<ApiResponse<SessionAnalysisSummaryResponseDTO>> getTodaySummary(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

        try {
            return ResponseEntity.ok(ApiResponse.success(aiAnalysisService.getTodayAverageSummary(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(404, e.getMessage()));
        }
    }

    // 연속 사용 일수 조회
    @GetMapping("/user/{userId}/streak")
    public ResponseEntity<ApiResponse<Integer>> getStreak(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

        return ResponseEntity.ok(ApiResponse.success(aiAnalysisService.getStreakDays(userId)));
    }

    // 최근 7일 주간 점수 조회
    @GetMapping("/user/{userId}/weekly")
    public ResponseEntity<ApiResponse<List<DailyScoreDTO>>> getWeeklyScores(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

        return ResponseEntity.ok(ApiResponse.success(aiAnalysisService.getWeeklyScores(userId)));
    }

    // 특정 날짜 일일 점수 조회
    @GetMapping("/user/{userId}/daily")
    public ResponseEntity<ApiResponse<DailyScoreDTO>> getDailyScore(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        userService.validatePatientAccess(authorizationHeader, userId);

        try {
            return ResponseEntity.ok(ApiResponse.success(aiAnalysisService.getDailyScore(userId, date)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.fail(404, e.getMessage()));
        }
    }

    // 최근 7일 분석 결과 조회
    @GetMapping("/recent7days/{userId}")
    public ResponseEntity<ApiResponse<List<RecentRiskAnalysisResponseDTO>>> getRecent7DaysAnalysis(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Integer userId) {

        userService.validatePatientAccess(authorizationHeader, userId);

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