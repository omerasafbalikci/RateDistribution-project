package com.ratedistribution.tdp.utilities.exceptions;

/**
 * Thrown when loading the simulator configuration fails.
 * Typically, wraps IO or parsing-related issues.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class ConfigurationLoadException extends RuntimeException {
    public ConfigurationLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
