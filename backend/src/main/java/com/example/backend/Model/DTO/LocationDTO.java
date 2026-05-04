package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LocationDTO {
    private Integer userId;    // 어떤 관찰대상자인지
    private BigDecimal latitude;
    private BigDecimal longitude;
}
