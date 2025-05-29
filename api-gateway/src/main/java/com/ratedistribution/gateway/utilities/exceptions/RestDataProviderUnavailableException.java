package com.ratedistribution.gateway.utilities.exceptions;

/**
 * Exception thrown when the REST data provider is unavailable.
 * This typically indicates that the external service is down,
 * unreachable, or responding with errors.
 *
 * @author Ömer Asaf Balıkçı
 */

public class RestDataProviderUnavailableException extends RuntimeException {
    public RestDataProviderUnavailableException(String message) {
        super(message);
    }
}
