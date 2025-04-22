package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.config.SimulatorConfigLoader;
import com.ratedistribution.tdp.model.HolidayDefinition;
import com.ratedistribution.tdp.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RequiredArgsConstructor
public class HolidayCalendarService {
    private static final Logger log = LogManager.getLogger(HolidayCalendarService.class);
    private final SimulatorConfigLoader simulatorConfigLoader;

    public boolean isHoliday(LocalDateTime dateTime) {
        SimulatorProperties simulatorProperties = this.simulatorConfigLoader.currentSimulator();
        log.trace("Checking holiday for {}", dateTime);
        if (simulatorProperties.getHolidays() == null) return false;

        Instant instant = dateTime.toInstant(ZoneOffset.UTC);
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
