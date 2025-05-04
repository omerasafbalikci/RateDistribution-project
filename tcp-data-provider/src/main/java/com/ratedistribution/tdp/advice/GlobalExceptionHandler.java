package com.ratedistribution.tdp.advice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized exception handler for the TCP application.
 * Logs unexpected or critical errors.
 * Call `GlobalExceptionHandler.handle(e)` to log exceptions globally.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class GlobalExceptionHandler {
    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    public static void handle(Exception e) {
        log.error("Unhandled exception [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
    }

    public static void handle(String context, Exception e) {
        log.error("Error in [{}] [{}]: {}", context, e.getClass().getSimpleName(), e.getMessage(), e);
    }
}
