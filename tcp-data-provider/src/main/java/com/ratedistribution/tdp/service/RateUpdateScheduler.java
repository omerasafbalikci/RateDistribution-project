package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.net.SubscriptionManager;
import com.ratedistribution.tdp.utilities.serializer.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically updates all simulated rate values and broadcasts
 * the updated data to all subscribed clients via {@link SubscriptionManager}.
 * The scheduler supports dynamic reconfiguration and controlled shutdown.
 * Uses virtual threads for non-blocking scheduling.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RateUpdateScheduler {
    private static final Logger log = LogManager.getLogger(RateUpdateScheduler.class);
    private final RateSimulator simulator;
    private final SubscriptionManager subscriptionManager;
    private volatile long periodMillis;
    private volatile int maxUpdates;
    private final AtomicInteger tick = new AtomicInteger(0);
    private ScheduledExecutorService executorService;

    /**
     * Constructs the scheduler with update configuration and dependencies.
     *
     * @param simulator           The simulator providing rate updates.
     * @param subscriptionManager Manager to broadcast updates to clients.
     * @param periodMillis        Interval between updates (milliseconds).
     * @param maxUpdates          Maximum updates to run (0 = infinite).
     */
    public RateUpdateScheduler(RateSimulator simulator, SubscriptionManager subscriptionManager,
                               long periodMillis, int maxUpdates) {
        this.simulator = simulator;
        this.subscriptionManager = subscriptionManager;
        this.periodMillis = periodMillis;
        this.maxUpdates = maxUpdates;
    }

    /**
     * Starts the scheduler using a virtual-threaded single-threaded executor.
     */
    public void start() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }

        executorService = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        executorService.scheduleAtFixedRate(this::produce, 0, periodMillis, TimeUnit.MILLISECONDS);

        log.info("RateUpdateScheduler started with interval: {} ms", periodMillis);
    }

    /**
     * Reconfigures the scheduler with new settings and restarts execution.
     *
     * @param newPeriod     New update interval in milliseconds.
     * @param newMaxUpdates New maximum number of updates (0 = infinite).
     */
    public void reconfigure(long newPeriod, int newMaxUpdates) {
        this.periodMillis = newPeriod;
        this.maxUpdates = newMaxUpdates;
        tick.set(0);

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Scheduler executor did not terminate in time.");
                }
            } catch (InterruptedException e) {
                log.warn("Scheduler executor termination interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
        start();
    }

    /**
     * Executes one update cycle and broadcasts results.
     * Stops execution if the max update limit is reached.
     */
    private void produce() {
        if (maxUpdates > 0 && tick.incrementAndGet() >= maxUpdates) return;

        simulator.updateAllRates().forEach(rate ->
                subscriptionManager.broadcast(rate.getRateName(), JsonUtil.toJson(rate))
        );
    }

    /**
     * Gracefully stops the scheduler executor.
     */
    public void stop() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.warn("Scheduler did not shut down cleanly.");
                } else {
                    log.info("RateUpdateScheduler stopped.");
                }
            } catch (InterruptedException e) {
                log.error("Scheduler shutdown interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
