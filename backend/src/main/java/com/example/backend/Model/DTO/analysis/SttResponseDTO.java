package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SttResponseDTO {
    private Long recordId;
    private String transcriptText;
}
