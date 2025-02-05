package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegimeStatus implements Serializable {
    private VolRegime currentRegime;
    private int stepsInRegime;
}
