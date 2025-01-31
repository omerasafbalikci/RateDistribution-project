package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.MultiRateDefinition;
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
    private List<MultiRateDefinition> rates;
}
