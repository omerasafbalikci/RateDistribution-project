package com.ratedistribution.tdp.config;

import lombok.Data;

/**
 * Represents TCP-related configuration settings for the simulator.
 * This class holds host and port information typically loaded from a YAML config file.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
public class TcpConfig {
    private String host;
    private int port;
}