package com.ratedistribution.ratehub.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a simplified market rate containing bid, ask, and timestamp.
 * Used in calculated results and Kafka output.
 *
 * @author Ömer Asaf BALIKÇI
 */

public record Rate(String rateName,
                   BigDecimal bid,
                   BigDecimal ask,
                   Instant timestamp) implements Serializable {
}
