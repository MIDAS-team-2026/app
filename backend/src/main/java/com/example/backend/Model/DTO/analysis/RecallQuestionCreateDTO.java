package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecallQuestionCreateDTO {
    private Integer userId;
    private String questionText;
    private String questionType;
    private String category;
    private String expectedAnswer;
}