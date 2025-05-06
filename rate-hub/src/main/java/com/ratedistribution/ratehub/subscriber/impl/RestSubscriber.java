package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.auth.TokenProvider;
import com.ratedistribution.ratehub.coord.RateListener;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.AbstractSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * RestSubscriber is a concrete implementation of AbstractSubscriber that fetches
 * rate data from a REST API using HTTP polling.
 * Supports token-based authorization and periodic polling for each subscribed rate.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RestSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(RestSubscriber.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ScheduledExecutorService poller;
    private ExecutorService fetchPool;
    private final TokenProvider tokenProvider;

    /**
     * Constructs a new RestSubscriber instance.
     *
     * @param listener     the listener to notify on events
     * @param platformName name of the platform
     * @param baseUrl      base URL of the REST API
     * @param tp           token provider for authentication
     */
    public RestSubscriber(RateListener listener, String platformName, String baseUrl, TokenProvider tp) {
        super(listener, platformName);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.tokenProvider = tp;
    }

    /**
     * Starts the subscriber and initializes the polling scheduler.
     */
    @Override
    public void connect() {
        log.info("[RestSubscriber] Connecting to platform: {}", platform);
        super.connect();
        if (fetchPool == null) fetchPool = Executors.newFixedThreadPool(Math.max(4, 8));
        if (poller == null || poller.isShutdown()) {
            poller = Executors.newSingleThreadScheduledExecutor();
            poller.scheduleAtFixedRate(this::pollAll, 0, 500, TimeUnit.MILLISECONDS);
            log.debug("[RestSubscriber] Polling started every 500ms for {}", platform);
        }
    }

    /**
     * Disconnects the subscriber and shuts down the polling scheduler.
     */
    @Override
    public void disconnect() {
        log.info("[RestSubscriber] Disconnecting from platform: {}", platform);
        if (poller != null) {
            poller.shutdownNow();
            log.debug("[RestSubscriber] Polling stopped for {}", platform);
        }
        if (fetchPool != null) {
            fetchPool.shutdownNow();
        }
        super.disconnect();
    }

    /**
     * Periodically polls all subscribed rate symbols in parallel.
     */
    private void pollAll() {
        if (!running) return;
        log.trace("[RestSubscriber] Polling all subscribed rates for {}", platform);
        subs.forEach(r -> fetchPool.submit(() -> fetchRate(r)));
    }

    /**
     * Keeps the thread alive if needed, supporting run loop for compatibility.
     */
    @Override
    public void run() {
        log.trace("[RestSubscriber] run() loop started");
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[RestSubscriber] Polling thread interrupted for {}", platform);
            }
        }
    }

    /**
     * Fetches a single rate from the REST API and notifies the listener.
     *
     * @param rate the rate symbol to fetch
     */
    private void fetchRate(String rate) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "api/rates/" + rate))
                    .header("Authorization", "Bearer " + tokenProvider.get())
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            log.trace("[RestSubscriber] Sending request for rate {} to {}", rate, baseUrl);
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            markActivity();

            if (resp.statusCode() == 401) {
                log.warn("[RestSubscriber] Unauthorized response for {}. Forcing token refresh.", rate);
                tokenProvider.forceRefresh();
                return;
            }
            if (resp.statusCode() != 200) {
                log.error("[RestSubscriber] Failed to fetch {} (status={}): {}", rate, resp.statusCode(), resp.body());
                listener.onRateStatus(platform, rate, RateStatus.ERROR);
                return;
            }
            RawTick raw = mapper.readValue(resp.body(), RawTick.class);
            markReceived();
            log.debug("[RestSubscriber] Received RawTick for {}: bid={}, ask={}", rate, raw.bid(), raw.ask());
            listener.onRateAvailable(platform, raw.rateName(), raw);
            listener.onRateUpdate(platform, raw.rateName(), new RateFields(raw.bid(), raw.ask(), raw.timestamp()));
        } catch (Exception e) {
            log.error("[RestSubscriber] Error fetching rate {}: {}", rate, e.getMessage(), e);
            listener.onRateError(platform, rate, e);
        }
    }
}
