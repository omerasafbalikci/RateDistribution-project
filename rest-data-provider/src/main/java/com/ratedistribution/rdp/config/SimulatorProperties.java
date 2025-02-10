package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "simulator")
@RefreshScope
public class SimulatorProperties {
    private long updateIntervalMillis;
    private int maxUpdates;
    private List<List<Double>> correlationMatrix;
    private String modelType;
    private boolean enableRegimeSwitching;
    private boolean useMarkovSwitching;
    private List<List<Double>> regimeTransitionMatrix;
    private RegimeDefinition regimeLowVol;
    private RegimeDefinition regimeHighVol;
    private WeekendHandling weekendHandling;
    private List<SessionVolFactor> sessionVolFactors;
    private boolean volumeVolatilityScalingEnabled;
    private double volumeVolatilityFactor;
    private List<HolidayDefinition> holidays;
    private List<MultiRateDefinition> rates;
    private List<EventShockDefinition> eventShocks;
    private List<MacroIndicatorDefinition> macroIndicators;
}
