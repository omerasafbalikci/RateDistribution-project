package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
