package com.ratedistribution.tdp.config;

import com.ratedistribution.tdp.model.MultiRateDefinition;
import lombok.Data;

import java.util.List;

@Data
public class SimulatorProperties {
    private long updateIntervalMillis;
    private int maxUpdates;
    private List<List<Double>> correlationMatrix;
    private List<MultiRateDefinition> rates;
    private List<SessionVolFactor> sessionVolFactors;
    private WeekendHandling weekendHandling;
    private boolean enableRegimeSwitching;
    private RegimeDefinition regimeLowVol;
    private RegimeDefinition regimeHighVol;
    private List<HolidayDefinition> holidays;
}
