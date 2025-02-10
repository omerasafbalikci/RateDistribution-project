package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MacroIndicatorDefinition {
    private String name;
    private double value;
    private double sensitivityToDrift;
    private double sensitivityToVol;
}
