package com.ratedistribution.auth.utilities.exceptions;

/**
 * Exception thrown when a specific role cannot be found.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String message) {
        super(message);
    }
}
