package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.HolidayDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Log4j2
public class HolidayCalendarService {
    private final SimulatorProperties simulatorProperties;

    public boolean isHoliday(LocalDateTime dateTime) {
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
