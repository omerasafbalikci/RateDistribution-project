package com.ratedistribution.ratehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

/**
 * TokenProvider provides a cached JWT token from an authentication server.
 * Automatically refreshes the token if it is expired or about to expire.
 * Handles HTTP-based authentication and caches the access token.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RequiredArgsConstructor
public class TokenProvider {
    private static final Logger log = LogManager.getLogger(TokenProvider.class);
    private final String url;
    private final String username;
    private final String password;
    private final int skew;
    private final HttpClient client = HttpClient.newHttpClient();
    private volatile String token = "";
    private volatile Instant expiresAt = Instant.EPOCH;

    /**
     * Returns a valid token, refreshing it if necessary.
     *
     * @return valid JWT token string
     */
    public synchronized String get() {
        try {
            if (Instant.now().isAfter(expiresAt.minusSeconds(skew))) {
                log.debug("Token expired or near expiration. Refreshing...");
                refresh();
            } else {
                log.trace("Token is still valid. Returning cached token.");
            }
            return token;
        } catch (Exception e) {
            log.error("Exception occurred while getting token: {}", e.getMessage(), e);
            GlobalExceptionHandler.handle("TokenProvider.get", e);
            return token;
        }
    }

    /**
     * Forces a token refresh regardless of its current expiration.
     */
    public synchronized void forceRefresh() {
        try {
            log.info("Force refresh requested. Forcing token expiration.");
            expiresAt = Instant.EPOCH;
            refresh();
        } catch (Exception e) {
            log.error("Exception occurred while forcing token refresh: {}", e.getMessage(), e);
            GlobalExceptionHandler.handle("TokenProvider.forceRefresh", e);
        }
    }

    /**
     * Refreshes the token from the authentication server.
     */
    private void refresh() {
        try {
            log.debug("Attempting to refresh token from URL: {}", url);

            String body = String.format("""
                    { "username":"%s", "password":"%s" }""", username, password);

            log.trace("Sending auth request with body: {}", body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            log.debug("Auth server responded with status: {}", resp.statusCode());

            if (resp.statusCode() != 200) {
                String errorMessage = "Auth failed with status %d: %s"
                        .formatted(resp.statusCode(), resp.body());
                log.error("Authentication failed: {}", errorMessage);
                IllegalStateException ex = new IllegalStateException(errorMessage);
                GlobalExceptionHandler.handle("TokenProvider.refresh [AUTH RESPONSE]", ex);
                return;
            }

            Map<?, ?> json = new ObjectMapper().readValue(resp.body(), Map.class);
            token = (String) json.get("accessToken");
            long defaultExpSeconds = 600;
            expiresAt = Instant.now().plusSeconds(defaultExpSeconds);
            log.info("Token refreshed successfully. Expires at: {}", expiresAt);
        } catch (Exception e) {
            log.error("Exception occurred while refreshing token: {}", e.getMessage(), e);
            GlobalExceptionHandler.handle("TokenProvider.refresh", e);
        }
    }
}