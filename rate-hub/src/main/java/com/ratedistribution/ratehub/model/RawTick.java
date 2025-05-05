package com.ratedistribution.ratehub.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record RawTick(
        String rateName,
        BigDecimal bid,
        BigDecimal ask,
        Instant timestamp,
        BigDecimal dayOpen,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        BigDecimal dayChange,
        String dayChangePercent,
        long dayVolume,
        long lastTickVolume
) implements Serializable {
    public Rate toRate() {
        return new Rate(rateName, bid, ask, timestamp);
    }
}
