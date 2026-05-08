package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class LocationDTO {
    private Integer userId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timestamp;
}