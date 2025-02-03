package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.MultiRateDefinition;
import com.ratedistribution.rdp.model.RegimeDefinition;
import com.ratedistribution.rdp.model.SessionVolFactor;
import com.ratedistribution.rdp.model.WeekendHandling;
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

    // korelasyon
    private List<List<Double>> correlationMatrix;
    private List<MultiRateDefinition> rates;

    // session-based vol
    private List<SessionVolFactor> sessionVolFactors;

    // weekend
    private WeekendHandling weekendHandling;

    // regime switching
    private boolean enableRegimeSwitching;
    private RegimeDefinition regimeLowVol;
    private RegimeDefinition regimeHighVol;
}
