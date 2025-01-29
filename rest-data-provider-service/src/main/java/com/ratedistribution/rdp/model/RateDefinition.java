package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateDefinition {
    private String rateName;
    private double basePrice;
    private double drift;
    private double volatility;
    private double baseSpread;
    private double shockProbability;
    private double shockMultiplier;
    private int shockDuration;   // update cycle sayısı
    private double shockDecayRate;
    private double kappa;
    private double theta;
}
