package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules and runs periodic rate updates based on simulator config.
 * Uses async execution and stops when maxUpdates is reached.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Component
@RequiredArgsConstructor
@RefreshScope
@Log4j2
public class RateUpdateScheduler {
    private final RateSimulatorService rateSimulatorService;
    private final SimulatorProperties simulatorProperties;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final AtomicInteger updateCount = new AtomicInteger(0);

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
        Instant nextRunTime = Instant.now().plusMillis(intervalMillis);

        taskScheduler.schedule(this::performUpdate, nextRunTime);
        log.info("Next rate update scheduled in {} ms", intervalMillis);
    }

    /**
     * Executes one update cycle and logs results.
     * Stops scheduler if max update count is reached.
     */
    private void performUpdate() {
        int currentCount = updateCount.get();

        if (simulatorProperties.getMaxUpdates() > 0 && currentCount >= simulatorProperties.getMaxUpdates()) {
            log.info("Maximum update count ({}) reached. Scheduler stopping...", currentCount);
            taskScheduler.shutdown();
            return;
        }

        log.info("Executing rate update... Iteration {}", currentCount + 1);

        Executor executor = taskScheduler.getScheduledExecutor();

        CompletableFuture
                .supplyAsync(rateSimulatorService::updateAllRates, executor)
                .thenAccept(updatedRates -> {
                    int newCount = updateCount.incrementAndGet();
                    log.info("Rate update completed: iteration={}, updatedCount={}", newCount, updatedRates.size());
                    scheduleNextRun();
                })
                .exceptionally(ex -> {
                    log.error("Exception during rate update execution", ex);
                    scheduleNextRun();
                    return null;
                });
    }
}
