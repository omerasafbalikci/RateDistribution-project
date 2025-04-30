package com.ratedistribution.rdp.service.concretes;

import com.ratedistribution.rdp.config.SimulatorProperties;
import com.ratedistribution.rdp.model.HolidayDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Log4j2
public class HolidayCalendarService {
    private final SimulatorProperties simulatorProperties;

    public boolean isHoliday(Instant instant) {
        log.trace("Checking holiday for {}", instant);
        if (simulatorProperties.getHolidays() == null) return false;

        for (HolidayDefinition holiday : simulatorProperties.getHolidays()) {
            if (!instant.isBefore(holiday.getStartDateTime()) && instant.isBefore(holiday.getEndDateTime())) {
                return true;
            }
        }
        return false;
    }
}
