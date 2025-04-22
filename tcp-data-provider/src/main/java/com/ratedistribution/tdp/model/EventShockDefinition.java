package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventShockDefinition {
    private String name;
    private Instant dateTime;
    private double jumpMean;
    private double jumpVol;
}
