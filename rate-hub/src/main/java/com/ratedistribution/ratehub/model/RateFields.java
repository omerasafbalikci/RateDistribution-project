package com.ratedistribution.ratehub.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RateFields(BigDecimal bid, BigDecimal ask, LocalDateTime timestamp) {
}
