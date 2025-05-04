package com.ratedistribution.tdp.config;

import lombok.Data;

/**
 * Holds application-level configuration such as port and simulator settings.
 * Typically loaded from a YAML or JSON config file.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
public class ApplicationConfig {
    private TcpConfig tcp;
    private JwtConfig jwt;
    private RedisConfig redis;
    private SimulatorProperties simulator;
}
