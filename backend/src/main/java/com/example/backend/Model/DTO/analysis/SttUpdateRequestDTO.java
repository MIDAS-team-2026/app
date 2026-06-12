package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SttUpdateRequestDTO {
    private Long recordId;
    private String transcriptText;
}
