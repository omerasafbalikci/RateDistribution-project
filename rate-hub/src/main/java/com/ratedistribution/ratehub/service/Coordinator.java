package com.ratedistribution.ratehub.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.ratedistribution.ratehub.calculator.FormulaEngine;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.subscriber.RateListener;
import com.ratedistribution.ratehub.subscriber.Subscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator implements RateListener {
    private static final Logger log = LogManager.getLogger(Coordinator.class);
    private final HazelcastInstance hz;
    private final IMap<String, Rate> rawRates;
    private final IMap<String, Rate> calculatedRates;
    private final RateKafkaProducer kafkaProducer;
    private final FormulaEngine formulaEngine = new FormulaEngine();
    private final Map<String, CalcDef> calcDefinitions;
    private final ExecutorService executor = Executors.newCachedThreadPool(Thread.ofVirtual().factory());

    public Coordinator(HazelcastInstance hz, RateKafkaProducer kafkaProducer, Map<String, CalcDef> calcDefinitions) {
        this.hz = hz;
        this.rawRates = hz.getMap("rawRates");
        this.calculatedRates = hz.getMap("calcRates");
        this.kafkaProducer = kafkaProducer;
        this.calcDefinitions = calcDefinitions;
    }

    public void start(Collection<Subscriber> subscribers) {
        for (Subscriber subscriber : subscribers) {
            executor.execute(() -> {
                try {
                    subscriber.connect("", "");
                } catch (Exception e) {
                    log.error("Failed to connect subscriber {}", subscriber.name(), e);
                }
            });
        }
    }

    @Override
    public void onConnect(String platform, boolean status) {
        log.info("Connected to platform {}: {}", platform, status);
    }

    @Override
    public void onDisconnect(String platform, boolean status) {
        log.warn("Disconnected from platform {}: {}", platform, status);
    }

    @Override
    public void onRateAvailable(String platform, String rateName, Rate fullRate) {
        handleRateUpdate(platform, rateName, fullRate);
    }

    @Override
    public void onRateUpdate(String platform, String rateName, RateFields fields) {
        Rate rate = new Rate(rateName, fields.bid(), fields.ask(), fields.timestamp());
        handleRateUpdate(platform, rateName, rate);
    }

    @Override
    public void onRateStatus(String platform, String rateName, RateStatus status) {
        switch (status) {
            case SUBSCRIBED -> log.info("{}:{} is now subscribed.", platform, rateName);
            case UNSUBSCRIBED -> log.info("{}:{} unsubscribed.", platform, rateName);
            case ERROR -> log.warn("{}:{} encountered an error.", platform, rateName);
        }    }

    @Override
    public void onRateError(String platformName, String rateName, Throwable error) {
        log.error("Error received from platform {} for rate {}: {}", platformName, rateName, error.getMessage(), error);
    }

    private void handleRateUpdate(String platform, String rateName, Rate rate) {
        String key = platform + ":" + rateName;
        rawRates.put(key, rate);

        calcDefinitions.values().stream()
                .filter(def -> def.dependsOn().contains(rateName))
                .forEach(this::recalculate);
    }

    private void recalculate(CalcDef def) {
        try {
            Map<String, Rate> allRates = new HashMap<>();
            for (String dep : def.dependsOn()) {
                rawRates.entrySet().stream()
                        .filter(e -> e.getKey().endsWith(":" + dep))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .ifPresent(rate -> allRates.put(dep, rate));
            }

            BigDecimal bid = formulaEngine.eval(def.engine(), def.bidFormula(), allRates, def.helpers());
            BigDecimal ask = formulaEngine.eval(def.engine(), def.askFormula(), allRates, def.helpers());

            Rate calculated = new Rate(def.rateName(), bid, ask, LocalDateTime.now());
            calculatedRates.put(def.rateName(), calculated);
            kafkaProducer.send(toJson(calculated));
            log.info("Calculated rate {} sent to Kafka", def.rateName());

        } catch (Exception e) {
            log.error("Error while calculating rate: {}", def.rateName(), e);
        }
    }

    private String toJson(Rate rate) {
        return String.format("{\"rateName\":\"%s\",\"bid\":%s,\"ask\":%s,\"timestamp\":\"%s\"}",
                rate.rateName(), rate.bid(), rate.ask(), rate.timestamp());
    }

    public void shutdown() {
        executor.shutdownNow();
        kafkaProducer.close();
        hz.shutdown();
    }

    public record CalcDef(
            String rateName,
            String engine,
            String bidFormula,
            String askFormula,
            Map<String, BigDecimal> helpers,
            Set<String> dependsOn
    ) {
    }
}