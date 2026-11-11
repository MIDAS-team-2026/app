package com.example.backend.Controller;

import com.example.backend.Model.DTO.ApiResponse;
import com.example.backend.Model.DTO.chat.SessionStartResponseDTO;
import com.example.backend.Service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 대화 시작 시 세션 생성 API
     * @param userId 세션을 시작하는 환자의 ID
     * @return 생성된 sessionId + 오늘 고정질문 완료 여부
     */
    @PostMapping("/session/start")
    public ResponseEntity<ApiResponse<SessionStartResponseDTO>> startSession(@RequestParam("userId") Integer userId) {
        try {
            SessionStartResponseDTO response = chatSessionService.startSession(userId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.fail(500, "세션 생성 실패: " + e.getMessage()));
        }
    }
}