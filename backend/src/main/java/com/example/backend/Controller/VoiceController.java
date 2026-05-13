package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Service.VoiceService; // 서비스 변경
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    /**
     * 환자의 음성 답변 파일 업로드 및 DB 기록 생성
     * @param userId 환자 ID
     * @param sessionId 현재 진행 중인 대화 세션 ID
     * @param file 음성 파일
     * @return 생성된 AudioRecord의 ID (recordId)
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Long>> uploadVoice(
            @RequestParam("userId") Integer userId,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.fail(400, "파일이 비어있습니다."));
            }

            // S3 업로드 + DB 저장 후 생성된 ID 반환
            Long recordId = voiceService.uploadAndSave(userId, sessionId, file);

            return ResponseEntity.ok(ApiResponse.success(recordId));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.fail(500, "음성 처리 중 오류 발생: " + e.getMessage()));
        }
    }
}