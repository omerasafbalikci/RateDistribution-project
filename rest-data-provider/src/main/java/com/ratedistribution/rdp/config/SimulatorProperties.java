package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "simulator")
@RefreshScope
public class SimulatorProperties {
    @Positive
    private long updateIntervalMillis;
    @Min(0)
    private int maxUpdates;
    @Valid
    @NotNull
    private ShockConfigDefinition shockConfig;
    @Valid
    private List<@Valid EventShockDefinition> eventShocks;
    @DecimalMin("0.0")
    private double weekendGapVolatility;
    @DecimalMin("0.0")
    private double weekendShockFactor;
    @Valid
    private List<@Valid SessionVolFactor> sessionVolFactors;
    @Valid
    private List<@Valid HolidayDefinition> holidays;
    @Valid
    private List<@Valid MultiRateDefinition> rates;
}
