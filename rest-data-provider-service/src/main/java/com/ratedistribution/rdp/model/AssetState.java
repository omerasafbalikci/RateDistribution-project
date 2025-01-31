package com.ratedistribution.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetState implements Serializable {
    private double currentPrice;  // Son fiyat (S_t)
    private double currentSigma;  // Son volatilite (σ_t)
    private double lastReturn;    // Bir önceki adımın log-return (r_{t-1})
}
