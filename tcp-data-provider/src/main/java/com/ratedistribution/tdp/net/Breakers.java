package com.ratedistribution.tdp.net;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Duration;

/**
 * Defines Resilience 4j Circuit Breaker configurations used in the application.
 * Prevents cascading failures by temporarily halting execution when error thresholds are reached.
 *
 * <p>This class is designed as a utility holder for shared CircuitBreaker instances.
 * It currently provides a predefined circuit breaker for subscription-related operations.
 *
 * @author Ömer Asaf BALIKÇI
 */
public final class Breakers {
    private Breakers() {
    }

    private static final CircuitBreakerConfig CFG = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(15))
            .slidingWindowSize(20)
            .permittedNumberOfCallsInHalfOpenState(5)
            .build();

    public static final CircuitBreaker SUBSCRIPTION =
            CircuitBreaker.of("subscription-cb", CFG);
}