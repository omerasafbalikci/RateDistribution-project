package com.ratedistribution.auth.utilities.exceptions;

/**
 * Exception thrown when a user cannot be found.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
