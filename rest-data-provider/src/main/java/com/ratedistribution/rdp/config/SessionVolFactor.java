package com.ratedistribution.rdp.config;

import lombok.Data;

@Data
public class SessionVolFactor {
    private int startHour;
    private int endHour;
    private double volMultiplier;
}
