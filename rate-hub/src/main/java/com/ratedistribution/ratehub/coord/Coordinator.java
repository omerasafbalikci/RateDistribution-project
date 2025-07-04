package com.ratedistribution.ratehub.coord;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.ratedistribution.ratehub.auth.TokenProvider;
import com.ratedistribution.ratehub.config.ConfigChangeListener;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RateStatus;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.Subscriber;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;
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
import java.util.stream.Collectors;

/**
 * Coordinator class is responsible for managing subscribers, receiving raw tick data,
 * recalculating dependent rates, and broadcasting updates to Kafka and Hazelcast.
 * Implements the RateListener interface for reacting to real-time events.
 * This class uses:
 * - Hazelcast for in-memory distributed caching.
 * - Kafka for event streaming.
 * - ExpressionEvaluator (like Groovy/JS) for dynamic rate calculations.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class Coordinator implements RateListener, ConfigChangeListener, AutoCloseable {
    private static final Logger log = LogManager.getLogger(Coordinator.class);

    private final HazelcastInstance hazelcast;
    private final RateKafkaProducer kafka;
    private volatile Map<String, CalcDef> calcDefs;
    private volatile List<Subscriber> subscribers = new ArrayList<>();

    private final SubscriberLoader loaderTemplate;
    private final TokenProvider tokenProvider;
    private final MailService mailService;

    private final IMap<String, RawTick> rawTicks;
    private final IMap<String, Rate> calcRates;
    private final Map<String, Map<String, Rate>> platformRates = new ConcurrentHashMap<>();
    private final ExecutorService pool;
    private SubSupervisor supervisor;


    public Coordinator(
            HazelcastInstance hazelcast,
            RateKafkaProducer kafkaProducer,
            List<CoordinatorConfig.SubscriberCfg> initialSubscriberCfgs,
            TokenProvider tokenProvider,
            Map<String, CalcDef> initialCalcDefs,
            int threadPoolSize,
            MailService mailService
    ) {
        this.hazelcast = hazelcast;
        this.kafka = kafkaProducer;
        this.calcDefs = new HashMap<>(initialCalcDefs);
        this.loaderTemplate = new SubscriberLoader(initialSubscriberCfgs, this, tokenProvider);
        this.tokenProvider = tokenProvider;
        this.mailService = mailService;

        this.rawTicks = hazelcast.getMap("rawTicks");
        this.calcRates = hazelcast.getMap("calcRates");
        this.pool = Executors.newFixedThreadPool(Math.max(2, threadPoolSize),
                Thread.ofVirtual().factory());
    }

    @Override
    public void onConfigChange(CoordinatorConfig newConfig) {
        log.info("[Coordinator] Applying new configuration...");

        Map<String, CalcDef> newDefs = newConfig.toDefs().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            CalcDef def = e.getValue();
                            def.setCustomEvaluator(
                                    new DynamicScriptFormulaEngine(
                                            def.getEngine(),
                                            Path.of(def.getScriptPath())
                                    )
                            );
                            return def;
                        }
                ));
        this.calcDefs = newDefs;
        log.info("[Coordinator] calcDefs updated: {}", newDefs.keySet());

        List<String> oldNames = this.subscribers.stream()
                .map(Subscriber::name)
                .toList();
        List<String> newNames = newConfig.subscribers().stream()
                .map(CoordinatorConfig.SubscriberCfg::name)
                .toList();

        this.subscribers.stream()
                .filter(s -> !newNames.contains(s.name()))
                .forEach(s -> {
                    log.info("[Coordinator] Removing subscriber {}", s.name());
                    try {
                        s.close();
                    } catch (Exception ex) {
                        log.warn(ex);
                    }
                });

        SubscriberLoader loader = new SubscriberLoader(newConfig.subscribers(), this, tokenProvider);
        List<Subscriber> fresh = loader.load();

        fresh.stream()
                .filter(s -> !oldNames.contains(s.name()))
                .forEach(s -> {
                    log.info("[Coordinator] Starting new subscriber {}", s.name());
                    try {
                        s.connect();
                    } catch (Exception ex) {
                        log.warn(ex);
                    }
                });

        this.subscribers = fresh;
        log.info("[Coordinator] Subscriber update complete.");
    }

    /**
     * Starts the Coordinator and all subscribers.
     *
     * @param subs the list of subscribers to start and connect
     */
    public void start(Collection<Subscriber> subs) {
        log.info("[Coordinator] Starting coordinator with {} subscribers", subs.size());
        calcDefs.values().forEach(def -> {
            Path p = Path.of(def.getScriptPath());
            if (Files.notExists(p)) {
                log.error("[Coordinator] Script file {} not found for {}", p, def.getRateName());
                return;
            }
            def.setCustomEvaluator(new DynamicScriptFormulaEngine(def.getEngine(), p));
            log.debug("[Coordinator] Evaluator initialized for {}", def.getRateName());
        });

        this.subscribers = new ArrayList<>(subs);
        this.supervisor = new SubSupervisor(subscribers, mailService);
        supervisor.start();
        log.info("[Coordinator] Supervisor started");

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

    /**
     * Gracefully shuts down the coordinator, subscribers, and background services.
     */
    public void shutdown() {
        log.info("[Coordinator] Shutdown initiated");
        try {
            for (Subscriber subscriber : subscribers) {
                try {
                    subscriber.close();
                    log.debug("[Coordinator] Closed subscriber: {}", subscriber.name());
                } catch (Exception e) {
                    log.warn("[Coordinator] Failed to close subscriber: {}", subscriber.name(), e);
                }
            }
            if (supervisor != null) {
                supervisor.close();
                log.debug("[Coordinator] Supervisor closed");
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
        log.trace("[RateListener] New rate available: {}:{}", platform, rateName);
        accept(platform, rateName, rawTick);
    }

    @Override
    public void onRateUpdate(String platform, String rateName, RateFields delta) {
        RawTick synthetic = new RawTick(rateName, delta.bid(), delta.ask(), delta.timestamp(), null, null, null, null, null, 0, 0);
        log.trace("[RateListener] Rate update received: {}:{}", platform, rateName);
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

    /**
     * Accepts and processes a new or updated tick from a subscriber.
     *
     * @param platform the source platform
     * @param symbol   the rate symbol
     * @param tick     the raw tick data
     */
    private void accept(String platform, String symbol, RawTick tick) {
        try {
            rawTicks.put(platform + "_" + symbol, tick);
            platformRates.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(platform, tick.toRate());
            kafka.sendRawTickAsString(tick, platform);
            recalc(symbol);
            log.debug("[Coordinator] Raw tick accepted and forwarded: {}:{}", platform, symbol);
        } catch (Exception e) {
            log.error("[Coordinator] Error in accept for {}:{} - {}", platform, symbol, e.getMessage(), e);
        }
    }

    /**
     * Triggers recalculation for any CalcDef that depends on the updated symbol.
     *
     * @param updatedSymbol the symbol that changed
     */
    private void recalc(String updatedSymbol) {
        log.trace("[Coordinator] Triggering recalculations for {}", updatedSymbol);
        calcDefs.values().stream()
                .filter(def -> def.getDependsOn().contains(updatedSymbol))
                .forEach(this::calculate);
    }

    /**
     * Performs calculation for a given CalcDef using its expression evaluator.
     *
     * @param def the CalcDef to calculate
     */
    private void calculate(CalcDef def) {
        Map<String, Rate> vars = new HashMap<>();
        for (String dep : def.getDependsOn()) {
            Map<String, Rate> per = platformRates.get(dep);
            if (per == null || per.isEmpty()) return;
            per.forEach((pf, r) -> vars.put(pf + "_" + dep, r));
        }
        Map<String, BigDecimal> flat = flatten(vars, def.getHelpers());
        for (String dep : def.getDependsOn()) {
            for (Subscriber sub : subscribers) {
                String bidKey = sub.name() + "_" + dep + "_bid";
                String askKey = sub.name() + "_" + dep + "_ask";
                if (!flat.containsKey(bidKey) || !flat.containsKey(askKey)) {
                    log.warn("[Coordinator] Skipping {}: missing {} or {}", def.getRateName(), bidKey, askKey);
                    return;
                }
            }
        }
        try {
            ExpressionEvaluator ev = def.getCustomEvaluator();
            if (ev == null) return;
            BigDecimal bid = ev.evaluate("bid", vars, def.getHelpers());
            BigDecimal ask = ev.evaluate("ask", vars, def.getHelpers());
            if (bid == null || ask == null) return;
            Rate out = new Rate(def.getRateName(), bid, ask, Instant.now());
            calcRates.put(def.getRateName(), out);
            kafka.sendRateAsString(out);
        } catch (Exception e) {
            log.error("[Coordinator] calc error {}", def.getRateName(), e);
        }
    }

    private Map<String, BigDecimal> flatten(Map<String, Rate> vars, Map<String, BigDecimal> helpers) {
        Map<String, BigDecimal> m = new HashMap<>();
        vars.forEach((k, r) -> {
            m.put(k + "_bid", r.bid());
            m.put(k + "_ask", r.ask());
        });
        if (helpers != null) m.putAll(helpers);
        return m;
    }
}