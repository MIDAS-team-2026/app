package com.example.backend.Model.DTO.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionStartResponseDTO {
    private Long sessionId;
    private boolean fixedQuestionsDoneToday;
    private String openingQuestionText; // fixedQuestionsDoneToday=false일 때만 값이 있음
}
