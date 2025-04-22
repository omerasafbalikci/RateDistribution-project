package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
