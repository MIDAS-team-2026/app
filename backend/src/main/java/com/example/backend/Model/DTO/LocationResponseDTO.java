package com.example.backend.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LocationResponseDTO {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime recordedAt;
}