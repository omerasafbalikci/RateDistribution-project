package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MultiRateDefinition {
    private String rateName;
    private double initialPrice;
    private double drift;
    private double baseSpread;
    private GarchParams garchParams;
    private boolean useMeanReversion;
    private double kappa;
    private double theta;
}
