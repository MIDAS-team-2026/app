package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Python AI가 생성한 회상 질문을 INITIAL 녹음에 연결할 때 사용하는 DTO. */
@Getter
@Setter
@NoArgsConstructor
public class RecallRecordLinkDTO {
    private Long recordId;
    private Long recallQuestionId;
    private String answerRole;
}
