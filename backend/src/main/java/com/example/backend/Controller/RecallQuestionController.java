package com.example.backend.Controller;

import com.example.backend.Model.DTO.RecallQuestionResponseDTO;
import com.example.backend.Model.DTO.RecallQuestionUpdateDTO;
import com.example.backend.Service.RecallQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recall")
@RequiredArgsConstructor
public class RecallQuestionController {

    private final RecallQuestionService recallQuestionService;

    // 파이썬 또는 클라이언트에서 호출할 GET 엔드포인트
    @GetMapping("/questions/{userId}")
    public ResponseEntity<List<RecallQuestionResponseDTO>> getRecallQuestions(@PathVariable Integer userId) {
        List<RecallQuestionResponseDTO> response = recallQuestionService.getQuestionsByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateQuestion(@PathVariable Long id, @RequestBody RecallQuestionUpdateDTO dto) {
        recallQuestionService.updateQuestionInfo(id, dto);
        return ResponseEntity.ok("질문 메타데이터가 성공적으로 업데이트되었습니다.");
    }
}