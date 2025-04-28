package com.ratedistribution.ratehub.coord;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.Subscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator implements RateListener, AutoCloseable {
    private static final Logger log = LogManager.getLogger(Coordinator.class);
    /* ---------------- DI ---------------- */
    private final HazelcastInstance hazelcast;
    private final RateKafkaProducer kafka;
    private final Map<String, CalcDef> calcDefs;
    private final CalculationEngine engine = new CalculationEngine();

    /* ---------------- Cache ---------------- */
    private final IMap<String, RawTick> rawTicks;
    private final IMap<String, Rate> calcRates;

    /**
     * symbol  →  platform  →  last Rate
     */
    private final Map<String, Map<String, Rate>> platformRates = new ConcurrentHashMap<>();

    /* ---------------- Concurrency ---------------- */
    private final ExecutorService pool;

    /* ---------------- Constructor ---------------- */
    public Coordinator(HazelcastInstance hz,
                       RateKafkaProducer producer,
                       Map<String, CalcDef> defs,
                       int threadPoolSize) {
        this.hazelcast = hz;
        this.kafka = producer;
        this.calcDefs = defs;

        this.rawTicks = hz.getMap("rawTicks");
        this.calcRates = hz.getMap("calcRates");
        this.pool = Executors.newFixedThreadPool(Math.max(2, threadPoolSize),
                Thread.ofVirtual().factory());
    }

    /* ---------------- Lifecycle ---------------- */
    public void start(Collection<Subscriber> subs) {
        subs.forEach(s -> pool.execute(() -> {
            try {
                s.connect("", "");
            } catch (Exception e) {
                log.error("{} connect fail", s.name(), e);
            }
        }));
    }

    public void shutdown() {
        pool.shutdownNow();
        kafka.close();
        hazelcast.shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    /* ---------------- RateListener ---------------- */
    @Override
    public void onConnect(String p, boolean ok) {
        log.info("⇢ {} connected", p);
    }

    @Override
    public void onDisconnect(String p, boolean ok) {
        log.warn("⇠ {} disconnected", p);
    }

    @Override
    public void onRateStatus(String p, String s, RateStatus st) {
    }

    @Override
    public void onRateError(String p, String s, Throwable e) {
        log.error("ERR {}:{}", p, s, e);
    }

    @Override
    public void onRateAvailable(String p, String sym, RawTick t) {
        accept(p, sym, t);
    }

    @Override
    public void onRateUpdate(String p, String sym, RateFields f) {
        RawTick synthetic = new RawTick(sym, f.bid(), f.ask(), f.timestamp(), null, null, null, null, null, 0, 0);
        accept(p, sym, synthetic);
    }

    /* ---------------- Core Logic ---------------- */
    private void accept(String platform, String symbol, RawTick tick) {
        // 1) cache ham veri
        rawTicks.put(platform + "_" + symbol, tick);

        // 2) son platform verisini sakla
        platformRates
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                .put(platform, tick.toRate());

        // 3) Kafka yayını
        kafka.sendJson(tick);

        // 4) dependent hesaplamaları tetikle
        recalc(symbol);
    }

    private void recalc(String updatedSymbol) {
        calcDefs.values().stream()
                .filter(def -> def.dependsOn().contains(updatedSymbol))
                .forEach(this::calculate);
    }

    private void calculate(CalcDef def) {
        Map<String, Rate> vars = new HashMap<>();
        for (String dep : def.dependsOn()) {
            Map<String, Rate> m = platformRates.get(dep);
            if (m == null || m.isEmpty()) return; // eksik veri
            vars.put(dep, m.values().iterator().next());
        }
        try {
            BigDecimal bid = engine.eval(def.engine(), def.bidFormula(), vars, def.helpers());
            BigDecimal ask = engine.eval(def.engine(), def.askFormula(), vars, def.helpers());
            Rate calc = new Rate(def.rateName(), bid, ask, Instant.now());

            calcRates.put(def.rateName(), calc);
            kafka.sendJson(calc);
            log.debug("✓ Calculated {}", def.rateName());
        } catch (Exception ex) {
            log.error("Calc {}", def.rateName(), ex);
        }
    }
}