package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines probabilities and magnitudes of automatic random shocks.
 * Used in daily simulation to trigger unpredictable movements.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShockConfigDefinition {
    private double smallShockWeekly;
    private double mediumShockMonthly;
    private double bigShockYearly;
    private double smallShockMinPct;
    private double smallShockMaxPct;
    private double mediumShockMinPct;
    private double mediumShockMaxPct;
    private double bigShockMinPct;
    private double bigShockMaxPct;
}
