package com.ratedistribution.tdp.service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateUpdateScheduler {
    private final RateSimulator simulator;
    private final long updateIntervalMillis;
    private final int maxUpdates;
    private final AtomicInteger updateCount = new AtomicInteger(0);
    private final SubscriberCallback callback;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public interface SubscriberCallback {
        void onRatesUpdated(List<String> lines);
    }

    public RateUpdateScheduler(RateSimulator simulator, long updateIntervalMillis, int maxUpdates, SubscriberCallback callback) {
        this.simulator = simulator;
        this.updateIntervalMillis = updateIntervalMillis;
        this.maxUpdates = maxUpdates;
        this.callback = callback;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            if (maxUpdates > 0 && updateCount.get() >= maxUpdates) {
                scheduler.shutdown();
                return;
            }

            List<String> updatedLines = simulator.updateAllRates();
            callback.onRatesUpdated(updatedLines);
            updateCount.incrementAndGet();
        }, 0, updateIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
