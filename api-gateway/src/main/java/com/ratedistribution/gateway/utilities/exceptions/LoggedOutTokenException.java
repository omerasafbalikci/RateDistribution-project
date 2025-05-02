package com.ratedistribution.gateway.utilities.exceptions;

/**
 * Exception thrown when a token has been logged out and is no longer valid for authentication.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class LoggedOutTokenException extends RuntimeException {
    public LoggedOutTokenException(String message) {
        super(message);
    }
}
