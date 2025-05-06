package com.ratedistribution.ratehub.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a partial update to a rate,
 * typically used for delta updates from data sources.
 *
 * @author Ömer Asaf BALIKÇI
 */

public record RateFields(BigDecimal bid, BigDecimal ask, Instant timestamp) {
}
