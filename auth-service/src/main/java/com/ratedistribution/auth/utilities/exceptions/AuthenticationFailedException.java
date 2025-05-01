package com.ratedistribution.auth.utilities.exceptions;

/**
 * Exception thrown when authentication fails.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
