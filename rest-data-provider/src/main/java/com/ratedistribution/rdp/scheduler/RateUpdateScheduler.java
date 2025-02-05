package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.service.concretes.RateSimulatorServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class RateUpdateScheduler {
    private final RateSimulatorServiceImpl rateEngine;
    private final RedisTemplate<String, RateDataResponse> rateResponseRedisTemplate;
    private final SimulatorProperties simulatorProperties;
    private int updateCount = 0;

    @Scheduled(fixedRateString = "#{@simulatorProperties.updateIntervalMillis}")
    public void scheduledUpdate() {
        if (simulatorProperties.getMaxUpdates() > 0 && updateCount >= simulatorProperties.getMaxUpdates()) {
            log.info("Max updates reached, stopping simulation...");
            return;
        }

        List<RateDataResponse> updatedRates = this.rateEngine.updateAllRates();
        HashOperations<String, String, RateDataResponse> ops = this.rateResponseRedisTemplate.opsForHash();
        for (RateDataResponse r : updatedRates) {
            ops.put("RATES", r.getRateName(), r);
        }

        updateCount++;
        log.info("Rates updated: {}", updatedRates.size());
    }
}
