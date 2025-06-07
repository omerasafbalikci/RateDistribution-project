package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.config.SimulatorConfigLoader;
import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.model.HolidayDefinition;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

/**
 * Service responsible for determining whether a given instant falls within a configured holiday period.
 * Holidays are used to pause rate updates during predefined intervals.
 * Loaded dynamically from {@code application-docker.yml} through {@link SimulatorConfigLoader}.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RequiredArgsConstructor
public class HolidayCalendarService {
    private static final Logger log = LogManager.getLogger(HolidayCalendarService.class);
    private final SimulatorConfigLoader simulatorConfigLoader;

    /**
     * Checks if the given {@link Instant} falls within any defined holiday period.
     *
     * @param instant The timestamp to check.
     * @return {@code true} if the time is within a holiday, otherwise {@code false}.
     */
    public boolean isHoliday(Instant instant) {
        log.trace("Checking if {} is during a holiday period", instant);
        SimulatorProperties simulatorProperties = this.simulatorConfigLoader.currentSimulator();
        if (simulatorProperties.getHolidays() == null || simulatorProperties.getHolidays().isEmpty()) {
            log.debug("No holidays configured.");
            return false;
        }

        for (HolidayDefinition holiday : simulatorProperties.getHolidays()) {
            Instant start = holiday.getStartDateTime();
            Instant end = holiday.getEndDateTime();

            log.trace("Checking holiday: {} - {}", start, end);
            if (!instant.isBefore(start) && instant.isBefore(end)) {
                log.debug("Holiday match found. Instant {} is between {} and {}", instant, start, end);
                return true;
            }
        }
        log.trace("No matching holiday period for {}", instant);
        return false;
    }
}
