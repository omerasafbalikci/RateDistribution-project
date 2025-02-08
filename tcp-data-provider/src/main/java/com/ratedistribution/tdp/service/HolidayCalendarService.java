package com.ratedistribution.tdp.service;

import com.ratedistribution.tdp.config.HolidayDefinition;
import com.ratedistribution.tdp.config.SimulatorProperties;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RequiredArgsConstructor
public class HolidayCalendarService {
    private final SimulatorProperties simulatorProperties;

    public boolean isHoliday(LocalDateTime dateTime) {
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);
        for (HolidayDefinition holiday : simulatorProperties.getHolidays()) {
            Instant start = holiday.getStartDateTime();
            Instant end = holiday.getEndDateTime();
            if (!instant.isBefore(start) && instant.isBefore(end)) {
                return true;
            }
        }
        return false;
    }
}
