package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

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
    private String aiReplyText;
    private String episodeId;
    private String conversationTopic;
    private String conversationStage;
    private Integer turnOrder;
    private LocalDateTime recordedAt;
    private Long presentedRecallQuestionId;
}
