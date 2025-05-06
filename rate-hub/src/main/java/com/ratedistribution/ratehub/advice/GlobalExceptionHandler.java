package com.ratedistribution.ratehub.advice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized exception handler for the rate-hub application.
 * Logs unexpected or critical errors.
 * Call `GlobalExceptionHandler.handle(e)` to log exceptions globally.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class GlobalExceptionHandler {
    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    public static void handle(String context, Exception e) {
        log.error("Error in [{}] [{}]: {}", context, e.getClass().getSimpleName(), e.getMessage(), e);
    }

    public static void fatal(String context, Exception e) {
        log.fatal("Fatal error in [{}]: {} - {}", context, e.getClass().getSimpleName(), e.getMessage(), e);
        System.exit(1);
    }
}
