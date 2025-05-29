package com.ratedistribution.gateway.utilities;

import com.ratedistribution.common.JwtValidator;
import io.jsonwebtoken.Claims;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility class for handling JWT token operations such as validation, role extraction, and checking if a token
 * is logged out. This class also integrates Redis for token management.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Component
@Log4j2
public class JwtUtil {
    private final JwtValidator delegate;

    /**
     * Constructs a JwtUtil instance by initializing the underlying JwtValidator
     * with required JWT and Redis configuration.
     *
     * @param secretKey      the base64 URL-encoded secret key
     * @param authoritiesKey the claim key used to extract roles
     * @param redisHost      Redis host for logout token tracking
     * @param redisPort      Redis port
     */
    public JwtUtil(@Value("${jwt.secret-key}") String secretKey,
                   @Value("${jwt.authorities-key}") String authoritiesKey,
                   @Value("${redis.host}") String redisHost,
                   @Value("${redis.port}") int redisPort) {
        this.delegate = new JwtValidator(secretKey, authoritiesKey, redisHost, redisPort);
    }

    /**
     * Validates the given JWT token and returns its claims.
     *
     * @param token the JWT token
     * @return the token claims if valid
     */
    public Claims getClaimsAndValidate(String token) {
        return delegate.getClaimsAndValidate(token);
    }

    /**
     * Checks whether the given token has been logged out using Redis.
     *
     * @param token the JWT token
     * @return true if token is logged out; false otherwise
     */
    public boolean isLoggedOut(String token) {
        return delegate.isLoggedOut(token);
    }

    /**
     * Extracts roles from the provided claims.
     *
     * @param claims the JWT claims
     * @return a list of roles
     */
    public List<String> getRoles(Claims claims) {
        return delegate.getRoles(claims);
    }
}
