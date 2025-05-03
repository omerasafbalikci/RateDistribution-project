package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a time-based volatility multiplier for specific trading hours.
 * Allows session-specific volume/volatility adjustments.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionVolFactor {
    private int startHour;
    private int endHour;
    private double volMultiplier;
}
