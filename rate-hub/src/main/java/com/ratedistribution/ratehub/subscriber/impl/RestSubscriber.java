package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RestSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(RestSubscriber.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ScheduledExecutorService poller;

    public RestSubscriber(RateListener listener, String platformName, String baseUrl, int ignored) {
        super(listener, platformName);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public void connect(String u, String p) {
        super.connect(u, p);
        if (poller == null || poller.isShutdown()) {
            poller = Executors.newSingleThreadScheduledExecutor();
            poller.scheduleAtFixedRate(this::pollAll, 0, 500, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void disconnect() {
        if (poller != null) {
            poller.shutdownNow();
        }
        super.disconnect();
    }

    private void pollAll() {
        if (!running) return;
        subs.parallelStream().forEach(this::fetchRate);
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void fetchRate(String rate) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "api/rates/" + rate))
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                listener.onRateStatus(platform, rate, RateStatus.ERROR);
                return;
            }
            RawTick raw = mapper.readValue(resp.body(), RawTick.class);
            markReceived();
            listener.onRateAvailable(platform, raw.rateName(), raw);
            listener.onRateUpdate(platform, raw.rateName(), new RateFields(raw.bid(), raw.ask(), raw.timestamp()));
        } catch (Exception e) {
            listener.onRateError(platform, rate, e);
        }
    }
}
