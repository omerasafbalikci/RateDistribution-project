package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShockConfigDefinition {
    @DecimalMin("0.0")
    private double smallShockWeekly;
    @DecimalMin("0.0")
    private double mediumShockMonthly;
    @DecimalMin("0.0")
    private double bigShockYearly;
    @DecimalMin("0.0")
    private double smallShockMinPct;
    @DecimalMin("0.0")
    private double smallShockMaxPct;
    @DecimalMin("0.0")
    private double mediumShockMinPct;
    @DecimalMin("0.0")
    private double mediumShockMaxPct;
    @DecimalMin("0.0")
    private double bigShockMinPct;
    @DecimalMin("0.0")
    private double bigShockMaxPct;
}
