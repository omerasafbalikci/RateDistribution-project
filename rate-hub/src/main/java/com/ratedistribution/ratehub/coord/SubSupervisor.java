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

/**
 * SubSupervisor is responsible for periodically monitoring the health and connection status
 * of all subscribers. It sends alerts when health checks fail and attempts automatic reconnection.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RequiredArgsConstructor
public class SubSupervisor implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(SubSupervisor.class);
    private final List<Subscriber> subscribers;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final MailService mailService;
    private final Map<String, Instant> lastAlertTime = new ConcurrentHashMap<>();

    /**
     * Starts periodic health checks for all subscribers.
     * Runs every 5 seconds in a single-threaded scheduler.
     */
    public void start() {
        log.info("[Supervisor] Starting supervisor with {} subscribers", subscribers.size());
        exec.scheduleAtFixedRate(this::probe, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Periodic task that performs:
     * - health check
     * - alerting if unhealthy
     * - reconnection if disconnected
     * - logging subscriber metrics
     */
    private void probe() {
        for (Subscriber s : subscribers) {
            try {
                boolean dataHealthy = s.healthCheck();
                boolean connHealthy = s.connectionHealthy();

                if (!connHealthy) maybeAlert(s, "connection lost");
                else if (!dataHealthy) maybeAlert(s, "no data for 10s");

                SubscriberMetrics m = s.metrics();
                log.info("[Metrics] {} conn={} dataHealthy={} recv={}", m.platform(), connHealthy, dataHealthy, m.receivedCount());

                if (!s.isConnected()) {
                    log.warn("[Supervisor] reconnecting {}", s.name());
                    s.connect();
                }
            } catch (Exception e) {
                log.error("[Supervisor] probe error for {}", s.name(), e);
            }
        }
    }

    private void maybeAlert(Subscriber s, String reason) {
        Instant now = Instant.now();
        Instant last = lastAlertTime.getOrDefault(s.name(), Instant.EPOCH);
        if (Duration.between(last, now).toMinutes() >= 10) {
            log.warn("[Supervisor] ALERT {} – {}", s.name(), reason);
            mailService.sendAlert(s.name(), "Subscriber " + s.name() + " " + reason);
            lastAlertTime.put(s.name(), now);
        }
    }

    /**
     * Gracefully stops the supervisor and shuts down the internal scheduler.
     */
    @Override
    public void close() {
        log.info("[Supervisor] Shutting down supervisor...");
        exec.shutdownNow();
    }
}