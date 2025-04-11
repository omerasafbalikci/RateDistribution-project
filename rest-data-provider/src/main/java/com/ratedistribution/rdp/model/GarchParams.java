package com.ratedistribution.rdp.model;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
