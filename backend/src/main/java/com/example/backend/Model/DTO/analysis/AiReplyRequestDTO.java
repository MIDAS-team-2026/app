package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Python AI가 답변 생성 완료 후 Spring에 저장 요청할 때 사용하는 DTO. */
@Getter
@Setter
@NoArgsConstructor
public class AiReplyRequestDTO {
    private Long recordId;
    private String replyText;
}
