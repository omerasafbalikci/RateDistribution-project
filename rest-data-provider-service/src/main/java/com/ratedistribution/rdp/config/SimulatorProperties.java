package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.RateDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "simulator")
@RefreshScope
@RequiredArgsConstructor
@Getter
@Setter
public class SimulatorProperties {
    private long updateIntervalMillis;
    private int maxUpdates;
    private double dynamicSpreadAlpha;
    private List<RateDefinition> rates;
}
