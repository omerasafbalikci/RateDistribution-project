package com.ratedistribution.ratehub.coord;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.Subscriber;
import com.ratedistribution.ratehub.utilities.DynamicScriptFormulaEngine;
import com.ratedistribution.ratehub.utilities.ExpressionEvaluator;
import com.ratedistribution.ratehub.utilities.MailService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator implements RateListener, AutoCloseable {
    private static final Logger log = LogManager.getLogger(Coordinator.class);
    private final HazelcastInstance hazelcast;
    private final RateKafkaProducer kafka;
    private final Map<String, CalcDef> calcDefs;
    private final IMap<String, RawTick> rawTicks;
    private final IMap<String, Rate> calcRates;
    private final Map<String, Map<String, Rate>> platformRates = new ConcurrentHashMap<>();
    private final ExecutorService pool;
    private final MailService mailService;
    private SubSupervisor supervisor;
    private List<Subscriber> subscribers = new ArrayList<>();

    public Coordinator(HazelcastInstance hz,
                       RateKafkaProducer producer,
                       Map<String, CalcDef> defs,
                       int threadPoolSize,
                       MailService mailService) {
        this.hazelcast = hz;
        this.kafka = producer;
        this.calcDefs = defs;
        this.rawTicks = hz.getMap("rawTicks");
        this.calcRates = hz.getMap("calcRates");
        this.pool = Executors.newFixedThreadPool(Math.max(2, threadPoolSize),
                Thread.ofVirtual().factory());
        this.mailService = mailService;
    }

    public void start(Collection<Subscriber> subs) {
        calcDefs.values().forEach(def -> {
            Path p = Path.of(def.getScriptPath());
            if (Files.notExists(p)) {
                log.error("Script file {} not found for {}", p, def.getRateName());
                return;
            }
            def.setCustomEvaluator(
                    new DynamicScriptFormulaEngine(def.getEngine(), p)
            );
        });

        this.subscribers = new ArrayList<>(subs);
        this.supervisor = new SubSupervisor(subscribers, mailService);
        supervisor.start();

        for (Subscriber subscriber : subscribers) {
            pool.execute(() -> {
                try {
                    subscriber.connect();
                    log.info("[Coordinator] Connected subscriber: {}", subscriber.name());
                } catch (Exception e) {
                    log.error("[Coordinator] Failed to connect subscriber: {}", subscriber.name(), e);
                }
            });
        }
    }

    public void shutdown() {
        try {
            for (Subscriber subscriber : subscribers) {
                try {
                    subscriber.close();
                } catch (Exception e) {
                    log.warn("[Coordinator] Failed to close subscriber: {}", subscriber.name(), e);
                }
            }
            if (supervisor != null) {
                supervisor.close();
            }
            pool.shutdownNow();
            kafka.close();
            hazelcast.shutdown();
            log.info("[Coordinator] Shutdown completed.");
        } catch (Exception ex) {
            log.error("[Coordinator] Error during shutdown", ex);
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public void onConnect(String platform, boolean status) {
        log.info("[RateListener] ⇢ Connected: {}", platform);
    }

    @Override
    public void onDisconnect(String platform, boolean status) {
        log.warn("[RateListener] ⇠ Disconnected: {}", platform);
    }

    @Override
    public void onRateAvailable(String platform, String rateName, RawTick rawTick) {
        accept(platform, rateName, rawTick);
    }

    @Override
    public void onRateUpdate(String platform, String rateName, RateFields delta) {
        RawTick synthetic = new RawTick(rateName, delta.bid(), delta.ask(), delta.timestamp(), null, null, null, null, null, 0, 0);
        accept(platform, rateName, synthetic);
    }

    @Override
    public void onRateStatus(String platform, String rateName, RateStatus status) {
        log.info("[RateListener] Rate status: {} - {} [{}]", platform, rateName, status);
    }

    @Override
    public void onRateError(String platformName, String rateName, Throwable error) {
        log.error("[RateListener] Error on {}:{} - {}", platformName, rateName, error.getMessage(), error);
    }

    private void accept(String platform, String symbol, RawTick tick) {
        try {
            rawTicks.put(platform + "_" + symbol, tick);

            platformRates
                    .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                    .put(platform, tick.toRate());

            kafka.sendRawTickAsString(tick, platform);

            recalc(symbol);
        } catch (Exception e) {
            log.error("[Coordinator] Error in accept for {}:{}", platform, symbol, e);
        }
    }

    private void recalc(String updatedSymbol) {
        calcDefs.values().stream()
                .filter(def -> def.getDependsOn().contains(updatedSymbol))   // ← getter
                .forEach(this::calculate);
    }

    private void calculate(CalcDef def) {

        /* Bağımlı kurların son değerlerini topla */
        Map<String, Rate> vars = new HashMap<>();
        for (String dep : def.getDependsOn()) {             // ← getter
            Map<String, Rate> m = platformRates.get(dep);
            if (m == null || m.isEmpty()) return;            // veri eksik
            vars.put(dep, m.values().iterator().next());
        }

        try {
            ExpressionEvaluator ev = def.getCustomEvaluator();          // ← getter
            if (ev == null) {                                           // evaluator henüz set edilmemiş
                log.warn("[Coordinator] Skipped {}, evaluator not ready", def.getRateName());
                return;
            }

            BigDecimal bid = ev.evaluate("bid", vars, def.getHelpers());   // ← getter
            BigDecimal ask = ev.evaluate("ask", vars, def.getHelpers());

            Rate r = new Rate(def.getRateName(), bid, ask, Instant.now()); // ← getter
            calcRates.put(def.getRateName(), r);
            kafka.sendRateAsString(r);
            log.debug("[Coordinator] ✓ Calculated {}", def.getRateName());

        } catch (Exception ex) {
            log.error("[Coordinator] Calc error for {} – {}", def.getRateName(), ex.getMessage(), ex);
        }
    }
}