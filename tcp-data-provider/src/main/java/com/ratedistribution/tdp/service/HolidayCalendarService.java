package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.config.SimulatorConfigLoader;
import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.model.HolidayDefinition;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

@RequiredArgsConstructor
public class HolidayCalendarService {
    private static final Logger log = LogManager.getLogger(HolidayCalendarService.class);
    private final SimulatorConfigLoader simulatorConfigLoader;

    public boolean isHoliday(Instant instant) {
        SimulatorProperties simulatorProperties = this.simulatorConfigLoader.currentSimulator();
        log.trace("Checking holiday for {}", instant);
        if (simulatorProperties.getHolidays() == null) return false;

        for (HolidayDefinition holiday : simulatorProperties.getHolidays()) {
            Instant start = holiday.getStartDateTime();
            Instant end = holiday.getEndDateTime();
            if (!instant.isBefore(start) && instant.isBefore(end)) {
                log.debug("Matched holiday period: {} - {}", start, end);
                return true;
            }
        }
        return false;
    }
}
