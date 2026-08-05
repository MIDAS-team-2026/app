package com.example.backend.Model.DTO.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FixedQuestionStatusDTO {
    private boolean doneToday;
    private boolean onboardingDone;
}
