package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.analysis.AiReplyRequestDTO;
import com.example.backend.Model.DTO.analysis.AiReplyResponseDTO;
import com.example.backend.Model.DTO.analysis.SessionRecordsResponseDTO;
import com.example.backend.Model.DTO.analysis.SttResponseDTO;
import com.example.backend.Model.DTO.analysis.SttUpdateRequestDTO;
import com.example.backend.Model.DTO.analysis.VoiceResponseDTO;
import com.example.backend.Service.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    /**
     * 환자의 음성 답변 파일 업로드 및 DB 기록 생성
     * @param userId 환자 ID
     * @param sessionId 현재 진행 중인 대화 세션 ID
     * @param recallQuestionId 회상 질문 ID (선택)
     * @param answerRole 답변 역할 (INITIAL: 최초 기준 답변 / RECALL: 회상 답변)
     * @param file 음성 파일
     * @return 생성된 AudioRecord의 ID (recordId)
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<VoiceResponseDTO>> uploadVoice(
            @RequestParam("userId") Integer userId,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "recallQuestionId", required = false) Long recallQuestionId,
            @RequestParam(value = "answerRole", required = false) String answerRole,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "파일이 비어있습니다."));
        }

        try {
            VoiceResponseDTO response = voiceService.uploadAndSave(
                    userId, sessionId, recallQuestionId, answerRole, file);

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (IOException e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.fail(500, "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * STT 결과 텍스트를 해당 AudioRecord에 반영
     * 요청 본문: { "recordId": Long, "transcriptText": String }
     */
    @PostMapping("/stt")
    public ResponseEntity<ApiResponse<Void>> updateStt(@RequestBody SttUpdateRequestDTO request) {
        if (request.getRecordId() == null) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "recordId는 필수입니다."));
        }
        if (request.getTranscriptText() == null) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.fail(400, "transcriptText는 필수입니다."));
        }

        try {
            voiceService.updateTranscript(request.getRecordId(), request.getTranscriptText());
            return ResponseEntity.ok(ApiResponse.success());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        }
    }

    /**
     * 특정 recordId에 대해 수동으로 STT 변환 수행
     */
    @PostMapping("/stt/{recordId}")
    public ResponseEntity<ApiResponse<SttResponseDTO>> performStt(@PathVariable Long recordId) {
        try {
            String transcriptText = voiceService.transcribeRecord(recordId);
            return ResponseEntity.ok(ApiResponse.success(new SttResponseDTO(recordId, transcriptText)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.fail(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502)
                    .body(ApiResponse.fail(502, e.getMessage()));
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
}