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

/**
 * SimulatorProperties maps simulator-related configuration properties from application.yml or config server.
 * It provides validation and dynamic refresh capabilities for simulator settings used in rate simulation logic.
 *
 * <p>Configuration prefix: <b>simulator</b></p>
 *
 * Supported configuration parameters:
 * <ul>
 *     <li><b>updateIntervalMillis</b> – Update interval in milliseconds (must be positive)</li>
 *     <li><b>maxUpdates</b> – Maximum number of updates (minimum 0)</li>
 *     <li><b>shockConfig</b> – General shock configuration</li>
 *     <li><b>eventShocks</b> – List of event-based shock definitions</li>
 *     <li><b>weekendGapVolatility</b> – Volatility factor for weekend gaps</li>
 *     <li><b>weekendShockFactor</b> – Shock factor applied during weekends</li>
 *     <li><b>sessionVolFactors</b> – Volatility adjustment factors per session</li>
 *     <li><b>holidays</b> – Holiday definitions affecting simulation</li>
 *     <li><b>rates</b> – Definitions of multi-rate simulation sources</li>
 * </ul>
 *
 * This bean is annotated with {@link RefreshScope} to allow live reloading from the configuration server.
 *
 * @author Ömer Asaf BALIKÇI
 */

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
