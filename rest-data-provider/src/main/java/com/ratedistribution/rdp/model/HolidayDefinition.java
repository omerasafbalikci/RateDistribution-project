package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a simulation holiday range.
 * Affects trading sessions and volatility injection.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HolidayDefinition {
    @NotBlank
    private String name;
    @NotNull
    private Instant startDateTime;
    @NotNull
    private Instant endDateTime;
}
