package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class HolidayDefinition {
    @NotBlank
    private String name;
    @NotNull
    private Instant startDateTime;
    @NotNull
    private Instant endDateTime;
}
