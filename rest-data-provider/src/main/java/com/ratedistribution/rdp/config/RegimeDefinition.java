package com.ratedistribution.rdp.config;

import lombok.Data;

@Data
public class RegimeDefinition {
    private double volScale;
    private int meanDuration;
    private double transitionProb;
}
