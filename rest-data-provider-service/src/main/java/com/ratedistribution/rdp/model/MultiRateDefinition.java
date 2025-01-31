package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MultiRateDefinition {
    private String rateName;
    private double initialPrice;
    private GarchParams garchParams;
    private double jumpIntensity;
    private double jumpMean;
    private double jumpVol;
    private double drift;
    private double baseSpread;
    private boolean useMeanReversion;
    private double kappa;
    private double theta;
}
