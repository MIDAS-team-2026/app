package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 앱이 폴링할 때 반환하는 DTO.
 * replyText == null → 아직 AI가 답변 생성 중.
 * replyText != null → 답변 준비 완료.
 */
@Getter
@AllArgsConstructor
public class AiReplyResponseDTO {
    private Long recordId;
    private String replyText;
}
