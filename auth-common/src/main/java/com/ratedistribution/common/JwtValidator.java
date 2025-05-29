package com.ratedistribution.common;

import com.ratedistribution.common.exceptions.InvalidTokenException;
import com.ratedistribution.common.exceptions.TokenNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JwtValidator is a utility class responsible for validating JWT tokens,
 * extracting roles, and checking logout status using Redis.
 * It supports token signature verification and claim extraction.
 * This class is commonly used in authentication and authorization middleware.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class JwtValidator {
    private static final Logger log = LogManager.getLogger(JwtValidator.class);
    private final SecretKey key;
    private final String rolesClaim;
    private final JedisPool jedisPool;

    /**
     * Constructs a JwtValidator with the given secret, roles claim key, and Redis configuration.
     *
     * @param secretBase64Url the base64 URL-encoded secret key for JWT verification
     * @param rolesClaim      the name of the claim that holds user roles
     * @param redisHost       the Redis server host
     * @param redisPort       the Redis server port
     */
    public JwtValidator(String secretBase64Url,
                        String rolesClaim,
                        String redisHost,
                        int redisPort) {
        this.key = getSignInKey(secretBase64Url);
        this.rolesClaim = rolesClaim;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
    }

    /**
     * Validates the provided JWT token and returns the associated claims.
     *
     * @param token the JWT token to validate.
     * @return the claims extracted from the token if valid.
     * @throws InvalidTokenException if the token is invalid.
     */
    public Claims getClaimsAndValidate(String token) {
        log.trace("Entering getClaimsAndValidate method in JwtUtils");
        log.debug("Validating token: {}", token);
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            log.info("Token validated successfully. Claims: {}", claims);
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            log.error("Error while parsing token: {}", exception.getMessage());
            throw new InvalidTokenException("Invalid token");
        } finally {
            log.trace("Exiting getClaimsAndValidate method in JwtUtils");
        }
    }

    /**
     * Extracts roles from the provided claims.
     *
     * @param claims the claims extracted from the JWT token.
     * @return a list of roles associated with the claims.
     */
    public List<String> getRoles(Claims claims) {
        log.trace("Entering getRoles method in JwtUtils");
        log.debug("Extracting roles from claims: {}", claims);
        Object rolesObject = claims.get(this.rolesClaim);
        if (rolesObject instanceof List<?>) {
            List<String> roles = new ArrayList<>();
            for (Object role : (List<?>) rolesObject) {
                if (role instanceof String) {
                    roles.add((String) role);
                }
            }
            log.info("Roles extracted: {}", roles);
            log.trace("Exiting getRoles method in JwtUtils");
            return roles;
        }
        log.warn("No roles found in claims or roles are not of the expected type.");
        return Collections.emptyList();
    }

    /**
     * Checks if the provided token has been logged out using Redis.
     *
     * @param token the JWT token to check.
     * @return true if the token is logged out, false otherwise.
     * @throws TokenNotFoundException if the token or its status is not found in Redis.
     */
    public boolean isLoggedOut(String token) {
        log.trace("Entering isLoggedOut method in JwtUtils");
        log.debug("Checking if token is logged out: {}", token);
        try (Jedis jedis = this.jedisPool.getResource()) {
            String tokenIdStr = jedis.get(token);
            if (tokenIdStr == null) {
                log.error("Token not found in Redis: {}", token);
                throw new TokenNotFoundException("Token not found in Redis");
            }

            long tokenId = Long.parseLong(tokenIdStr);
            String key = "token:" + tokenId + ":is_logged_out";
            String value = jedis.get(key);
            if (value == null) {
                log.error("Logout status information not found for token: {}", token);
                throw new TokenNotFoundException("Token's logout status information not found in Redis");
            }
            boolean isLoggedOut = Boolean.parseBoolean(value);
            log.info("Token {} logout status: {}", token, isLoggedOut);
            return isLoggedOut;
        } finally {
            log.trace("Exiting isLoggedOut method in JwtUtils");
        }
    }

    /**
     * Converts a base64 URL-encoded secret key into a usable HMAC SHA key.
     *
     * @param secretKey the base64 URL-encoded secret
     * @return the generated SecretKey instance
     */
    private SecretKey getSignInKey(String secretKey) {
        log.trace("Entering getSignInKey method in JwtUtils");
        log.debug("Retrieving secret key for JWT validation.");
        byte[] keyBytes = Decoders.BASE64URL.decode(secretKey);
        SecretKey sKey = Keys.hmacShaKeyFor(keyBytes);
        log.debug("Secret key retrieved successfully.");
        log.trace("Exiting getSignInKey method in JwtUtils");
        return sKey;
    }
}
