package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Represents the real-time state of an asset during simulation.
 * Holds pricing, volatility, volume and regime info.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetState implements Serializable {
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
