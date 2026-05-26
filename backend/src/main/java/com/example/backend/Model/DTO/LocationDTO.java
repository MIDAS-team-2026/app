package com.example.backend.Model.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LocationDTO {
    private Integer userId;
    private BigDecimal latitude;
    private BigDecimal longitude;
}