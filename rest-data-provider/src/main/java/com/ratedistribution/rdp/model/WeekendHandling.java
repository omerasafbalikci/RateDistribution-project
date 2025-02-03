package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeekendHandling {
    private boolean enabled;
    private double weekendGapJumpMean;
    private double weekendGapJumpVol;
}
