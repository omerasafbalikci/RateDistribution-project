package com.ratedistribution.ratehub.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.ratedistribution.ratehub.calculator.FormulaEngine;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
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
    private final RateKafkaProducer kafka;
    private final FormulaEngine engine = new FormulaEngine();
    private final Map<String, CalcDef> defs;
    private final ExecutorService pool;

    public Coordinator(HazelcastInstance hz, RateKafkaProducer kafka, Map<String, CalcDef> defs, int threads) {
        this.hz = hz;
        this.rawRates = hz.getMap("rawRates");
        this.calculatedRates = hz.getMap("calcRates");
        this.kafka = kafka;
        this.defs = defs;
        this.pool = Executors.newFixedThreadPool(threads, Thread.ofVirtual().factory());
    }

    public void registerAndStart(Collection<Subscriber> subs) {
        subs.forEach(this::start);
    }

    private void start(Subscriber s) {
        pool.execute(() -> {
            try {
                s.connect("", "");
            } catch (Exception e) {
                log.error("{} connect fail", s.name(), e);
            }
        });
    }

    public void shutdown() {
        pool.shutdownNow();
        kafka.close();
        hz.shutdown();
    }

    @Override
    public void onConnect(String p, boolean ok) {
        log.info("{} connected: {}", p, ok);
    }

    @Override
    public void onDisconnect(String p, boolean ok) {
        log.warn("{} disconnected", p);
    }

    @Override
    public void onRateAvailable(String p, String r, Rate full) {
        handleUpdate(p, r, full);
    }

    @Override
    public void onRateUpdate(String p, String r, RateFields f) {
        handleUpdate(p, r, new Rate(r, f.bid(), f.ask(), f.timestamp()));
    }

    @Override
    public void onRateStatus(String p, String r, com.ratedistribution.ratehub.model.RateStatus s) {
        log.info("{}:{} -> {}", p, r, s);
    }

    @Override
    public void onRateError(String p, String r, Throwable t) {
        log.error("Error {}:{}", p, r, t);
    }

    private void handleUpdate(String platform, String rateName, Rate rate) {
        rawRates.put(platform + ":" + rateName, rate);
        kafka.send(toLineProtocol(platform, rate));
        defs.values().stream().filter(d -> d.dependsOn.contains(rateName)).forEach(this::recalculate);
    }

    private void recalculate(CalcDef d) {
        try {
            Map<String, Rate> vars = new HashMap<>();
            d.dependsOn.forEach(dep -> rawRates.keySet().stream().filter(k -> k.endsWith(":" + dep)).findFirst().ifPresent(k -> vars.put(dep, rawRates.get(k))));
            if (vars.size() != d.dependsOn.size()) return;
            BigDecimal bid = engine.eval(d.engine, d.bidFormula, vars, d.helpers);
            BigDecimal ask = engine.eval(d.engine, d.askFormula, vars, d.helpers);
            Rate calc = new Rate(d.rateName, bid, ask, LocalDateTime.now());
            calculatedRates.put(d.rateName, calc);
            kafka.send(toLineProtocol("", calc));
            log.debug("Calculated {}", d.rateName);
        } catch (Exception e) {
            log.error("calc {}", d.rateName, e);
        }
    }

    private static String toLineProtocol(String platform, Rate r) {
        String symbol = platform.isEmpty() ? r.rateName() : platform + "_" + r.rateName();
        return String.format("%s|%s|%s|%s", symbol, r.bid(), r.ask(), r.timestamp());
    }

    public record CalcDef(String rateName, String engine, String bidFormula, String askFormula,
                          Map<String, BigDecimal> helpers, Set<String> dependsOn) {
    }
}