package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Schedules and runs periodic rate updates based on simulator config.
 * Uses async execution and stops when maxUpdates is reached.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Component
@RequiredArgsConstructor
@Log4j2
public class RateUpdateScheduler {
    private final RateSimulatorService rateSimulatorService;
    private final SimulatorProperties simulatorProperties;
    private final ThreadPoolTaskScheduler taskScheduler;
    private int updateCount = 0;

    /**
     * Starts the scheduler after component initialization.
     */
    @PostConstruct
    public void startScheduler() {
        scheduleNextRun();
    }

    /**
     * Schedules the next rate update based on configured interval.
     */
    private void scheduleNextRun() {
        long intervalMillis = simulatorProperties.getUpdateIntervalMillis();

        taskScheduler.schedule(() -> {
            performUpdate();
            scheduleNextRun();
        }, Instant.now().plusMillis(intervalMillis));

        log.info("Next update scheduled after {} ms", intervalMillis);
    }

    /**
     * Executes one update cycle and logs results.
     * Stops scheduler if max update count is reached.
     */
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
