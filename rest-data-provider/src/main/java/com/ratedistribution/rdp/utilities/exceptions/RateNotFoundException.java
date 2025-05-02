package com.ratedistribution.rdp.utilities.exceptions;

/**
 * Exception thrown when a requested rate is not found in the system.
 * Used primarily in Redis or service lookup operations.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RateNotFoundException extends RuntimeException {
    public RateNotFoundException(String message) {
        super(message);
    }
}
