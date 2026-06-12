package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class VoiceResponseDTO {
    private Long audioRecordId;
    private String audioFilePath;
    private String transcriptText;
    private Integer turnOrder;
    private LocalDateTime recordedAt;
}
