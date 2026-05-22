package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LinkRequestDTO {
    private Integer protectorId;
    private String patientCode;
}
