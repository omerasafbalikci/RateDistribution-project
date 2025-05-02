package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GARCH(1,1) model parameters for volatility simulation.
 * omega + alpha * prev^2 + beta * prevVol^2
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GarchParams {
    @DecimalMin("0.0")
    private double omega;
    @DecimalMin("0.0")
    private double alpha;
    @DecimalMin("0.0")
    private double beta;
}
