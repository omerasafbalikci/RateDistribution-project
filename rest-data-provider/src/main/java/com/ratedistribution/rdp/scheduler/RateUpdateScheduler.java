package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.service.abstracts.RateSimulatorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Log4j2
public class RateUpdateScheduler {
    private final RateSimulatorService rateSimulatorService;
    private final SimulatorProperties simulatorProperties;

    private ScheduledExecutorService executor;
    private int updateCount = 0;

    @PostConstruct
    public void startScheduler() {
        executor = Executors.newSingleThreadScheduledExecutor();
        long interval = simulatorProperties.getUpdateIntervalMillis();

        executor.scheduleAtFixedRate(() -> {
            try {
                performUpdate();
            } catch (Exception e) {
                log.error("Error in scheduled update => ", e);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    private void performUpdate() {
        if (simulatorProperties.getMaxUpdates() > 0 && updateCount >= simulatorProperties.getMaxUpdates()) {
            log.info("Max updates reached => stopping...");
            executor.shutdown();
            return;
        }
        List<RateDataResponse> updatedRates = rateSimulatorService.updateAllRates();
        updateCount++;
        log.info("Rates updated => iteration={} totalUpdated={}", updateCount, updatedRates.size());
    }
}
