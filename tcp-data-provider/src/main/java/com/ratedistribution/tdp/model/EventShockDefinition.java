package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Defines a critical market shock scheduled at a specific time.
 * Used to simulate events like news, crashes, or announcements.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventShockDefinition {
    private String name;
    private Instant dateTime;
    private double jumpMean;
    private double jumpVol;
}
