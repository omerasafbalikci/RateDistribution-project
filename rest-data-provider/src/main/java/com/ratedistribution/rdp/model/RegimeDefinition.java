package com.ratedistribution.rdp.model;

import lombok.Data;

@Data
public class RegimeDefinition {
    private double volScale;
    private int meanDuration;
    private double transitionProb;
}
