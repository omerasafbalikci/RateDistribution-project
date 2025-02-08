package com.ratedistribution.tdp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GarchParams {
    private double omega;
    private double alpha;
    private double beta;
}
