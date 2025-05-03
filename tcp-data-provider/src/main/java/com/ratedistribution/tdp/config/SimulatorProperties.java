package com.ratedistribution.tdp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratedistribution.tdp.model.*;
import lombok.Data;

import java.util.List;

/**
 * Holds the configuration parameters for the rate simulation engine.
 * These values are typically loaded from a YAML file and used
 * throughout the simulation lifecycle.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
public class SimulatorProperties {
    @JsonProperty("update-interval-millis")
    private long updateIntervalMillis;
    @JsonProperty("max-updates")
    private int maxUpdates;
    private ShockConfigDefinition shockConfig;
    private List<EventShockDefinition> eventShocks;
    @JsonProperty("weekendGapVolatility")
    private double weekendGapVolatility;
    @JsonProperty("weekendShockFactor")
    private double weekendShockFactor;
    private List<SessionVolFactor> sessionVolFactors;
    private List<HolidayDefinition> holidays;
    private List<MultiRateDefinition> rates;
}
