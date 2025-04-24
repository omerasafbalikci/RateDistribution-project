package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.subscriber.AbstractSubscriber;
import com.ratedistribution.ratehub.subscriber.RateListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RestSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(RestSubscriber.class);
    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RestSubscriber(RateListener l, String name, String host, int ignored) {
        super(l, name);
        this.baseUrl = host;
    }

    @Override
    public void run() {
        while (running) {
            for (String rate : getSubscribedRates()) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/rates/" + rate)).build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        listener.onRateStatus(platform, rate, RateStatus.ERROR);
                        continue;
                    }

                    Rate parsed = mapper.readValue(response.body(), Rate.class);
                    received.incrementAndGet();

                    listener.onRateAvailable(platform, parsed.rateName(), parsed);
                    listener.onRateUpdate(platform, parsed.rateName(),
                            new RateFields(parsed.bid(), parsed.ask(), parsed.timestamp()));

                } catch (Exception e) {
                    listener.onRateError(platform, rate, e);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        disconnect();
    }
}
