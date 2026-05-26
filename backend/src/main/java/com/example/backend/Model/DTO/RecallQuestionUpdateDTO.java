package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class RecallQuestionUpdateDTO {
    private String questionType;
    private String expectedAnswer;
    private List<String> keywords;
}