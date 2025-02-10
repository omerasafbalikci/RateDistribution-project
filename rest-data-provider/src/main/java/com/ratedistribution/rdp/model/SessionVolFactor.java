package com.ratedistribution.rdp.model;

import lombok.Data;

@Data
public class SessionVolFactor {
    private int startHour;
    private int endHour;
    private double volMultiplier;
}
