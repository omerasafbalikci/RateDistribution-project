package com.ratedistribution.rdp.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines initial parameters for a simulated rate (instrument).
 * Includes price, drift, GARCH config and spread.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MultiRateDefinition {
    @NotBlank
    private String rateName;
    @DecimalMin("0.0")
    private double initialPrice;
    private double drift;
    @DecimalMin("0.0")
    private double baseSpread;
    @Valid
    @NotNull
    private GarchParams garchParams;
    private boolean useMeanReversion;
    @DecimalMin("0.0")
    private double kappa;
    @DecimalMin("0.0")
    private double theta;
}
