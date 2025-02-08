package com.ratedistribution.tdp.config;

import lombok.Data;

@Data
public class WeekendHandling {
    private boolean enabled;
    private double weekendGapJumpMean;
    private double weekendGapJumpVol;
}
