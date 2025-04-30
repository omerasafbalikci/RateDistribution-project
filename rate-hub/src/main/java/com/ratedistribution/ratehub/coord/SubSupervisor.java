package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.subscriber.Subscriber;
import com.ratedistribution.ratehub.subscriber.SubscriberMetrics;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SubSupervisor implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(SubSupervisor.class);
    private final List<Subscriber> subscribers;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    public void start() {
        exec.scheduleAtFixedRate(this::probe, 5, 5, TimeUnit.SECONDS);
    }

    private void probe() {
        for (Subscriber subscriber : subscribers) {
            try {
                SubscriberMetrics metrics = subscriber.metrics();
                log.info("[Metrics] Platform: {}, Connected: {}, Received: {}, Health: {}",
                        metrics.platform(),
                        metrics.connected(),
                        metrics.receivedCount(),
                        subscriber.healthCheck()
                );

                if (!subscriber.isConnected()) {
                    log.warn("[Supervisor] {} is disconnected! Attempting to reconnect...", subscriber.name());
                    subscriber.connect("", "");
                }
            } catch (Exception e) {
                log.error("[Supervisor] Error while probing subscriber {}: {}", subscriber.name(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}