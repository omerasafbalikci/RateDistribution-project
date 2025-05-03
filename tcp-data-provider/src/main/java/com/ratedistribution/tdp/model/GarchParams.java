package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GARCH(1,1) model parameters for volatility simulation.
 * omega, alpha, and beta are standard GARCH coefficients.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GarchParams {
    private double omega;
    private double alpha;
    private double beta;
}
