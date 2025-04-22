package com.ratedistribution.tdp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratedistribution.tdp.model.*;
import lombok.Data;

import java.util.List;

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
