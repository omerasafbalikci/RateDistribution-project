package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Represents the current state of an asset being simulated.
 * Tracks price, volatility, regime, and daily stats.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetState {
    private double currentPrice;
    private double currentSigma;
    private double lastReturn;
    private double dayOpen;
    private double dayHigh;
    private double dayLow;
    private long dayVolume;
    private VolRegime currentRegime;
    private int stepsInRegime;
    private long lastUpdateEpochMillis;
    private LocalDate currentDay;
    private String configSignature;
}
