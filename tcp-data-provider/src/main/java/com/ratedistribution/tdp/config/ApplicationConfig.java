package com.ratedistribution.tdp.config;

import lombok.Data;

@Data
public class ApplicationConfig {
    private int port;
    private SimulatorProperties simulator;
}
