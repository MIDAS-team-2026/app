package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final S3Service s3Service;

    /**
     * 환자의 음성 답변 파일 업로드
     * @param file 프론트엔드에서 전달한 음성 파일 (MultipartFile)
     * @return S3에 저장된 파일의 접근 가능한 URL
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadVoice(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.fail(400, "파일이 비어있습니다."));
            }

            String uploadedUrl = s3Service.uploadFile(file);

            return ResponseEntity.ok(ApiResponse.success(uploadedUrl));

        } catch (IOException e) {
            return ResponseEntity.ok(ApiResponse.fail(500, "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}