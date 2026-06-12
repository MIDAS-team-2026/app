package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionRecordsResponseDTO {
    private Long recordId;
    private String transcriptText;
    private String audioFilePath;
    private String answerRole; // INITIAL, RECALL
    private Long recallQuestionId;
    private Long parentRecordId;
}