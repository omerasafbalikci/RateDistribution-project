package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegimeDefinition {
    private double volScale;
    private int meanDuration;
    private double transitionProb;
}
