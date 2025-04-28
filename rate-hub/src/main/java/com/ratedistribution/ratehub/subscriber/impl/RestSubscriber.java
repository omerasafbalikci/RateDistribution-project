package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.AbstractSubscriber;
import com.ratedistribution.ratehub.coord.RateListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RestSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(RestSubscriber.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RestSubscriber(RateListener l, String name, String host, int ignored) {
        super(l, name);
        this.baseUrl = host.endsWith("/") ? host : host + "/";
    }

    @Override
    public void run() {
        while (running) {
            subs.forEach(this::fetch);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        disconnect();
    }

    private void fetch(String rate) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/rates/" + rate)).timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                listener.onRateStatus(platform, rate, RateStatus.ERROR);
                return;
            }
            RawTick rawTick = mapper.readValue(response.body(), RawTick.class);
            received.incrementAndGet();
            listener.onRateAvailable(platform, rawTick.rateName(), rawTick);
            listener.onRateUpdate(platform, rawTick.rateName(), new RateFields(rawTick.bid(), rawTick.ask(), rawTick.timestamp()));
        } catch (Exception e) {
            listener.onRateError(platform, rate, e);
        }
    }
}
