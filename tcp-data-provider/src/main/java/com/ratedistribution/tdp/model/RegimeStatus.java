package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegimeStatus {
    private VolRegime currentRegime;
    private int stepsInRegime;
}
