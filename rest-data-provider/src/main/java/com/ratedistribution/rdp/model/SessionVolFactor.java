package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVolFactor {
    private int startHour;
    private int endHour;
    private double volMultiplier;
}
