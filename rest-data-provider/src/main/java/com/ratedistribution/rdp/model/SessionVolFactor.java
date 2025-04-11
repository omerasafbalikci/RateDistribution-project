package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVolFactor {
    @Min(0)
    @Max(23)
    private int startHour;
    @Min(1)
    @Max(24)
    private int endHour;
    @DecimalMin("0.0")
    private double volMultiplier;
}
