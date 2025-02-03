package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVolFactor {
    private int startHour;       // 0-23
    private int endHour;         // 1-24
    private double volMultiplier;
}
