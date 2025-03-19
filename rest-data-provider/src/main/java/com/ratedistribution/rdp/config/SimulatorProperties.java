package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "simulator")
@RefreshScope
public class SimulatorProperties {
    private long updateIntervalMillis;
    private int maxUpdates;
    private String modelType;
    private List<List<Double>> correlationMatrix;
    private ShockConfigDefinition shockConfig;
    private List<EventShockDefinition> eventShocks;
    private List<MacroIndicatorDefinition> macroIndicators;
    private double weekendGapVolatility;
    private double weekendShockFactor;
    private List<SessionVolFactor> sessionVolFactors;
    private List<HolidayDefinition> holidays;
    private List<MultiRateDefinition> rates;
}
