package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.analysis.RecallRecordLinkDTO;
import com.example.backend.Model.DTO.analysis.AiReplyRequestDTO;
import com.example.backend.Model.DTO.analysis.AiReplyResponseDTO;
import com.example.backend.Model.DTO.analysis.FixedQuestionStatusDTO;
import com.example.backend.Model.DTO.analysis.SessionRecordsResponseDTO;
import com.example.backend.Model.DTO.analysis.TextMessageRequestDTO;
import com.example.backend.Model.DTO.analysis.VoiceResponseDTO;
import com.example.backend.Service.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    /**
     * 사용자가 입력한 텍스트 메시지를 저장하고 AI 응답 생성을 트리거한다.
     * (stt_debug: 녹음/STT 업로드 대신 텍스트를 바로 전송받는 경로)
     * @param request userId, sessionId, text, recallQuestionId(선택), answerRole(선택)
     * @return 생성된 AudioRecord의 ID (recordId)
     */
    @PostMapping("/text")
    public ResponseEntity<ApiResponse<VoiceResponseDTO>> sendText(@RequestBody TextMessageRequestDTO request) {
        if (request.getUserId() == null || request.getSessionId() == null) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "userId와 sessionId는 필수입니다."));
        }
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "text는 필수입니다."));
        }

        try {
            VoiceResponseDTO response = voiceService.saveTextAndTrigger(
                    request.getUserId(),
                    request.getSessionId(),
                    request.getRecallQuestionId(),
                    request.getAnswerRole(),
                    request.getText());

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    /**
     * Python AI가 생성한 회상 질문을 INITIAL 녹음에 연결.
     * 요청 본문: { "recordId": Long, "recallQuestionId": Long, "answerRole": "INITIAL" }
     */
    @PostMapping("/recall-link")
    public ResponseEntity<ApiResponse<Void>> linkRecallQuestion(@RequestBody RecallRecordLinkDTO request) {
        if (request.getRecordId() == null || request.getRecallQuestionId() == null) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "recordId와 recallQuestionId는 필수입니다."));
        }
        try {
            voiceService.linkRecallQuestion(
                    request.getRecordId(),
                    request.getRecallQuestionId(),
                    request.getAnswerRole());
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    /**
     * Python AI가 답변 생성 완료 후 호출하는 API.
     * 요청 본문: { "recordId": Long, "replyText": String }
     */
    @PostMapping("/reply")
    public ResponseEntity<ApiResponse<Void>> saveAiReply(@RequestBody AiReplyRequestDTO request) {
        if (request.getRecordId() == null || request.getReplyText() == null) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "recordId와 replyText는 필수입니다."));
        }
        try {
            voiceService.saveAiReply(request.getRecordId(), request.getReplyText());
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    /**
     * 앱이 AI 답변을 폴링하는 API.
     * replyText == null → 아직 생성 중 / replyText != null → 완료
     */
    @GetMapping("/reply/{recordId}")
    public ResponseEntity<ApiResponse<AiReplyResponseDTO>> getAiReply(@PathVariable Long recordId) {
        try {
            AiReplyResponseDTO dto = voiceService.getAiReply(recordId);
            return ResponseEntity.ok(ApiResponse.success(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    @GetMapping("/session/{sessionId}/records")
    public ResponseEntity<List<SessionRecordsResponseDTO>> getSessionRecords(@PathVariable Long sessionId) {
        return ResponseEntity.ok(voiceService.getRecordsBySession(sessionId));
    }

    /**
     * 오늘 고정질문을 이미 완료했는지 + 온보딩(최초 5문항)을 마쳤는지 조회. Python AI가 호출한다.
     */
    @GetMapping("/fixed-status/{userId}")
    public ResponseEntity<ApiResponse<FixedQuestionStatusDTO>> getFixedQuestionStatus(@PathVariable Integer userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(voiceService.getFixedQuestionStatus(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    /**
     * 오늘 고정질문을 완료했다고 기록. Python AI가 호출한다.
     * @param onboarding true면 최초 5문항 온보딩이 이번에 완료된 것 (onboarding 플래그도 함께 설정)
     */
    @PostMapping("/fixed-complete/{userId}")
    public ResponseEntity<ApiResponse<Void>> markFixedQuestionsComplete(
            @PathVariable Integer userId,
            @RequestParam(value = "onboarding", defaultValue = "false") boolean onboarding) {
        try {
            voiceService.markFixedQuestionsDoneToday(userId, onboarding);
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }
}