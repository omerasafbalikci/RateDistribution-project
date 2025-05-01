package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.subscriber.Subscriber;
import com.ratedistribution.ratehub.subscriber.SubscriberMetrics;
import com.ratedistribution.ratehub.utilities.MailService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SubSupervisor implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(SubSupervisor.class);
    private final List<Subscriber> subscribers;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final MailService mailService;
    private final Map<String, Instant> lastAlertTime = new ConcurrentHashMap<>();

    public void start() {
        exec.scheduleAtFixedRate(this::probe, 5, 5, TimeUnit.SECONDS);
    }

    private void probe() {
        for (Subscriber subscriber : subscribers) {
            try {
                boolean healthy = subscriber.healthCheck();
                if (!healthy) {
                    Instant now = Instant.now();
                    Instant lastSent = lastAlertTime.getOrDefault(subscriber.name(), Instant.EPOCH);
                    if (Duration.between(lastSent, now).toMinutes() >= 10) {
                        mailService.sendAlert(subscriber.name(), "Subscriber " + subscriber.name() + " is unhealthy");
                        lastAlertTime.put(subscriber.name(), now);
                    }
                }
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