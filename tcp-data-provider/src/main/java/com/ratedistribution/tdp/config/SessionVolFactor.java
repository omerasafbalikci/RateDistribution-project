package com.ratedistribution.tdp.config;

import lombok.Data;

@Data
public class SessionVolFactor {
    private int startHour;
    private int endHour;
    private double volMultiplier;
}
