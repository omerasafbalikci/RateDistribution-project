package com.ratedistribution.gateway.controller;

import com.ratedistribution.gateway.utilities.exceptions.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * FallbackController provides fallback methods for each microservice in case of service unavailability.
 * When a microservice is down or unavailable, the Gateway redirects the request to the appropriate fallback method.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RestController
@Log4j2
public class FallbackController {
    /**
     * Fallback method for the authentication service.
     * Triggered when the authentication service is unavailable.
     * Logs the error and throws an AuthServiceUnavailableException.
     *
     * @return ResponseEntity with a fallback message for the auth service.
     */
    @RequestMapping(value = "/fallback/auth", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> fallbackAuth() {
        log.trace("Entering fallbackAuth method in FallbackController");
        try {
            log.error("Auth service is unavailable, executing fallback method");
            throw new AuthServiceUnavailableException("Auth service is temporarily unavailable. Please try again later.");
        } finally {
            log.trace("Exiting fallbackAuth method in FallbackController");
        }
    }

    /**
     * Fallback method for the user management service.
     * Triggered when the user management service is unavailable.
     * Logs the error and throws a UserServiceUnavailableException.
     *
     * @return ResponseEntity with a fallback message for the user management service.
     */
    @RequestMapping(value = "/fallback/user", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> fallbackUser() {
        log.trace("Entering fallbackUser method in FallbackController");
        try {
            log.error("User service is unavailable, executing fallback method");
            throw new UserServiceUnavailableException("User management service is temporarily unavailable. Please try again later.");
        } finally {
            log.trace("Exiting fallbackUser method in FallbackController");
        }
    }

    /**
     * Fallback method for the rest data provider.
     * Triggered when the rest data provider is unavailable.
     * Logs the error and throws a RestDataProviderUnavailableException.
     *
     * @return ResponseEntity with a fallback message for the rest data provider.
     */
    @RequestMapping(value = "/fallback/rest", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> fallbackRest() {
        log.trace("Entering fallbackRest method in FallbackController");
        try {
            log.error("Rest data provider is unavailable, executing fallback method");
            throw new RestDataProviderUnavailableException("Rest data provider is temporarily unavailable. Please try again later.");
        } finally {
            log.trace("Exiting fallbackRest method in FallbackController");
        }
    }
}
