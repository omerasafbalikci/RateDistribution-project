package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Defines a holiday period during which the market is closed.
 * Affects rate update scheduling and simulation pause logic.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HolidayDefinition {
    private String name;
    private Instant startDateTime;
    private Instant endDateTime;
}
