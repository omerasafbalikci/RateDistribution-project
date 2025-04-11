package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventShockDefinition {
    @NotBlank
    private String name;
    @NotNull
    private Instant dateTime;
    private double jumpMean;
    @DecimalMin("0.0")
    private double jumpVol;
}
