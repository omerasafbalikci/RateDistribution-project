package com.ratedistribution.tdp.config;

import lombok.Data;

import java.time.Instant;

@Data
public class HolidayDefinition {
    private String name;
    private Instant startDateTime;
    private Instant endDateTime;
}
