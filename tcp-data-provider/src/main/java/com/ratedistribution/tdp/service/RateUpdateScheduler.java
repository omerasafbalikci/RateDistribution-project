package com.ratedistribution.tdp.service;

import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RateUpdateScheduler implements Runnable {
    private final RateSimulator simulator;
    private final long updateIntervalMillis;
    private final int maxUpdates;
    private final AtomicInteger updateCount = new AtomicInteger(0);
    private final SubscriberCallback callback;

    public interface SubscriberCallback {
        void onRatesUpdated(List<String> lines);
    }

    public RateUpdateScheduler(RateSimulator simulator, long updateIntervalMillis, int maxUpdates, SubscriberCallback callback) {
        this.simulator = simulator;
        this.updateIntervalMillis = updateIntervalMillis;
        this.maxUpdates = maxUpdates;
        this.callback = callback;
    }

    @Override
    public void run() {
        while (maxUpdates <= 0 || updateCount.get() < maxUpdates) {
            try {
                List<String> updatedLines = simulator.updateAllRates();
                callback.onRatesUpdated(updatedLines);
                updateCount.incrementAndGet();

                Thread.sleep(updateIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
