package com.ratedistribution.ratehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

@RequiredArgsConstructor
public class TokenProvider {
    private final String url;
    private final String username;
    private final String password;
    private final int skew;

    private final HttpClient client = HttpClient.newHttpClient();
    private volatile String token = "";
    private volatile Instant expiresAt = Instant.EPOCH;

    public synchronized String get() {
        if (Instant.now().isAfter(expiresAt.minusSeconds(skew))) {
            refresh();
        }
        return token;
    }

    public synchronized void forceRefresh() {
        expiresAt = Instant.EPOCH;
        refresh();
    }

    private void refresh() {
        try {
            String body = String.format("""
                { "username":"%s", "password":"%s" }""", username, password);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new IllegalStateException("Auth failed: " + resp.body());

            Map<?, ?> json = new ObjectMapper().readValue(resp.body(), Map.class);

            token = (String) json.get("accessToken");

            // expires_in gelmediği için sabit bir süre kullan (örnek: 10 dakika)
            long defaultExpSeconds = 600;
            expiresAt = Instant.now().plusSeconds(defaultExpSeconds);

        } catch (Exception e) {
            throw new IllegalStateException("Token alınamadı", e);
        }
    }
}