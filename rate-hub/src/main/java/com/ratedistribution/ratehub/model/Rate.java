package com.ratedistribution.ratehub.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Rate(String rateName,
                   BigDecimal bid,
                   BigDecimal ask,
                   Instant timestamp) {
}
