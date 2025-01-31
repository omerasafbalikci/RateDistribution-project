package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.service.abstracts.RateService;
import com.ratedistribution.rdp.service.concretes.MultiAssetRateEngineServiceImpl;
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
    private final MultiAssetRateEngineServiceImpl rateEngine;
    private final RedisTemplate<String, RateDataResponse> rateResponseRedisTemplate;
    private final SimulatorProperties simulatorProperties;

    @Scheduled(fixedRateString = "#{@simulatorProperties.updateIntervalMillis}")
    public void scheduledUpdate(){
        List<RateDataResponse> updatedRates = rateEngine.updateAllRates();
        // Kaydet
        HashOperations<String, String, RateDataResponse> ops = rateResponseRedisTemplate.opsForHash();
        for(RateDataResponse r : updatedRates){
            ops.put("RATES", r.getRateName(), r);
        }
        log.info("Rates updated: {}", updatedRates.size());
    }
}
