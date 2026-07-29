package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TextMessageRequestDTO {
    private Integer userId;
    private Long sessionId;
    private String text;
    private Long recallQuestionId;
    private String answerRole;
}
