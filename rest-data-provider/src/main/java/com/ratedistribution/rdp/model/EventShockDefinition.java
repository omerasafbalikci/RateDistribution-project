package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Defines an event-based shock with a specific datetime and jump parameters.
 * Used for injecting volatility during simulation.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventShockDefinition {
    @NotBlank
    private String name;
    @NotNull
    private Instant dateTime;
    @NotNull
    private double jumpMean;
    @DecimalMin("0.0")
    private double jumpVol;
}
