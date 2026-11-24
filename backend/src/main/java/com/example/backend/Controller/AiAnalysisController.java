package com.example.backend.Controller;

import com.example.backend.Model.DTO.*;
import com.example.backend.Model.DTO.analysis.DailyScoreDTO;
import com.example.backend.Model.DTO.analysis.LinguisticMarkerDTO;
import com.example.backend.Model.DTO.analysis.LinguisticMarkerHistoryItemDTO;
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

    // 개인 기준선(z-score) 계산용으로 Python이 과거 세션 지표들을 가져갈 때 사용
    // Python/FastAPI 연동용이므로 현재는 인증 적용하지 않음
    @GetMapping("/linguistic-markers/user/{userId}")
    public ResponseEntity<ApiResponse<List<LinguisticMarkerHistoryItemDTO>>> getLinguisticMarkerHistory(
            @PathVariable Integer userId) {
        return ResponseEntity.ok(ApiResponse.success(aiAnalysisService.getLinguisticMarkerHistory(userId)));
    }

    // 세션별 최종 분석 요약 조회
    // 세션의 소유자 본인 또는 연동된 보호자만 조회할 수 있다.
    @GetMapping("/session/{sessionId}/summary")
    public ResponseEntity<SessionAnalysisSummaryResponseDTO> getSessionSummary(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long sessionId) {

        // 로그인 여부를 먼저 확인한 뒤(401), 세션 소유자 기준으로 접근 권한을 검사한다(403).
        userService.getAuthenticatedUser(authorizationHeader);
        userService.validatePatientAccess(authorizationHeader, aiAnalysisService.getSessionOwnerId(sessionId));

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
    public ResponseEntity<String> completeSession(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long sessionId) {

        // 세션의 주인 본인(또는 연동된 보호자)만 세션을 종료할 수 있다.
        userService.validatePatientAccess(authorizationHeader, chatSessionService.getSessionOwnerId(sessionId));

        chatSessionService.endSession(sessionId);
        aiAnalysisService.completeSessionAndTriggerBatch(sessionId);
        return ResponseEntity.ok("세션이 성공적으로 종료되었으며 분석 트리거가 완료되었습니다.");
    }
}