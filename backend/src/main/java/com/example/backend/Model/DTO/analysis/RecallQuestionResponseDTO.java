package com.example.backend.Model.DTO.analysis;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class RecallQuestionResponseDTO {
    private Long questionId;
    private String questionText;
    private String questionType;
    private String category;
    private List<String> keywords;
    private String expectedAnswer;
}