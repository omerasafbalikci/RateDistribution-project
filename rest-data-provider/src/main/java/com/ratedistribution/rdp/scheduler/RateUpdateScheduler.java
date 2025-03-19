package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Log4j2
public class RateUpdateScheduler {
    private final RateSimulatorService rateSimulatorService;
    private final SimulatorProperties simulatorProperties;
    private final ThreadPoolTaskScheduler taskScheduler;
    private int updateCount = 0;

    @PostConstruct
    public void startScheduler() {
        long intervalMillis = simulatorProperties.getUpdateIntervalMillis();
        Duration interval = Duration.ofMillis(intervalMillis);

        taskScheduler.scheduleAtFixedRate(this::performUpdate, Instant.now(), interval);
        log.info("Rate update scheduler started with interval: {} ms", intervalMillis);
    }

    private void performUpdate() {
        if (simulatorProperties.getMaxUpdates() > 0 && updateCount >= simulatorProperties.getMaxUpdates()) {
            log.info("Max updates reached => stopping...");
            taskScheduler.shutdown();
            return;
        }
        log.info("Starting rate update process... Iteration: {}", updateCount + 1);

        CompletableFuture.supplyAsync(rateSimulatorService::updateAllRates)
                .thenAccept(updatedRates -> {
                    updateCount++;
                    log.info("Rates updated => iteration={} totalUpdated={}", updateCount, updatedRates.size());
                })
                .exceptionally(ex -> {
                    log.error("Error during rate update process", ex);
                    return null;
                });
    }
}
