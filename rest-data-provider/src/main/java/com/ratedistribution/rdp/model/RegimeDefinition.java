package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegimeDefinition {
    private double volScale;     // bu rejimde sigma'ya çarpan
    private int meanDuration;    // ortalama adım
    private double transitionProb; // rejim değişim olasılığı
}
