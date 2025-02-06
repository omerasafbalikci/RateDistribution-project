package com.ratedistribution.rdp.service.abstracts;

import java.time.LocalDateTime;

public interface HolidayCalendarService {
    boolean isHoliday(LocalDateTime dateTime);
}
