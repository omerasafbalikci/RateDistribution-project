package com.ratedistribution.rdp.scheduler;

import com.ratedistribution.rdp.service.abstracts.RateService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class RateUpdateScheduler {
    private final RateService rateService;

    public RateUpdateScheduler(RateService rateService) {
        this.rateService = rateService;
    }

    @Scheduled(fixedRateString = "#{@simulatorProperties.updateIntervalMillis}")
    public void scheduledRateUpdate() {
        rateService.updateRates();
    }
}
