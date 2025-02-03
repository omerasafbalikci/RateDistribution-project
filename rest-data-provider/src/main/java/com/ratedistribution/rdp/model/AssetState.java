package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

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

    // Rejim durumu
    private VolRegime currentRegime;
    private int stepsInRegime;

    // Son güncelleme epoch
    private long lastUpdateEpochMillis;
    // EKLENDİ: gün bilgisini takip edelim
    private LocalDate currentDay;
}
