package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.net.SubscriptionManager;
import com.ratedistribution.tdp.utilities.serializer.JsonUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RateUpdateScheduler {
    private final RateSimulator sim;
    private final SubscriptionManager subs;
    private volatile long periodMillis;
    private volatile int maxUpdates;
    private volatile int tick = 0;
    private ScheduledExecutorService executorService;

    public RateUpdateScheduler(RateSimulator sim, SubscriptionManager subs,
                               long periodMillis, int maxUpdates) {
        this.sim = sim;
        this.subs = subs;
        this.periodMillis = periodMillis;
        this.maxUpdates = maxUpdates;
    }

    public void start() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::produce, 0, periodMillis, TimeUnit.MILLISECONDS);
        System.out.println("[SCHEDULER] Started with interval " + periodMillis + " ms");
    }

    public void reconfigure(long newPeriod, int newMaxUpdates) {
        this.periodMillis = newPeriod;
        this.maxUpdates = newMaxUpdates;
        this.tick = 0;
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.out.println("[SCHEDULER] Executor did not terminate in time.");
                }
            } catch (InterruptedException e) {
                System.out.println("[SCHEDULER] Executor termination interrupted.");
                Thread.currentThread().interrupt();
            }
        }
        start();
    }

    private void produce() {
        if (maxUpdates > 0 && tick++ >= maxUpdates) return;

        sim.updateAllRates().forEach(r -> {
            subs.broadcast(r.getRateName(), JsonUtil.toJson(r));
        });
    }
}
