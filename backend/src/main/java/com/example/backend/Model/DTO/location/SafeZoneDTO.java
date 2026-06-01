package com.example.backend.Model.DTO.location;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SafeZoneDTO {
    private Integer userId;    // 내부 식별용
    private String zoneName;   // "우리집" 등
    private Double latitude;
    private Double longitude;
    private Double radius;
}