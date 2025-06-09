package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.HolidayDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for checking whether a given timestamp falls within a holiday period.
 * Uses holiday definitions from {@link SimulatorProperties}.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Service
@RequiredArgsConstructor
@Log4j2
public class HolidayCalendarService {
    private final SimulatorProperties simulatorProperties;

    /**
     * Determines if the given instant is within any defined holiday period.
     *
     * @param instant the time to check
     * @return true if the time is within a holiday, false otherwise
     */
    public boolean isHoliday(Instant instant) {
        log.trace("Checking holiday for {}", instant);
        if (simulatorProperties.getHolidays() == null) {
            log.debug("No holiday definitions found.");
            return false;
        }

        for (HolidayDefinition holiday : simulatorProperties.getHolidays()) {
            log.trace("Evaluating holiday: {} ({} to {})", holiday.getName(), holiday.getStartDateTime(), holiday.getEndDateTime());
            if (!instant.isBefore(holiday.getStartDateTime()) && instant.isBefore(holiday.getEndDateTime())) {
                log.info("Timestamp {} is within holiday: {}", instant, holiday.getName());
                return true;
            }
        }
        log.debug("Timestamp {} is not within any holiday period.", instant);
        return false;
    }
}
