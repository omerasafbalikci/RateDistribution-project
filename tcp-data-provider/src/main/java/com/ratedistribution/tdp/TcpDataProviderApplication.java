package com.ratedistribution.tdp;

import com.ratedistribution.tdp.advice.GlobalExceptionHandler;
import com.ratedistribution.tdp.config.SimulatorConfigLoader;
import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.net.RatesTcpServer;
import com.ratedistribution.tdp.service.HolidayCalendarService;
import com.ratedistribution.tdp.service.RateSimulator;
import com.ratedistribution.tdp.service.RateUpdateScheduler;
import com.ratedistribution.tdp.service.ShockService;
import com.ratedistribution.tdp.utilities.exceptions.ConfigurationLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main application entry point for the TCP-based rate distribution provider.
 * Loads configuration, starts services and schedules rate updates.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class TcpDataProviderApplication {
    private static final Logger log = LogManager.getLogger(TcpDataProviderApplication.class);

    public static void main(String[] args) {
        log.info("Initializing TCP Data Provider Application...");

        SimulatorConfigLoader cfgLoader;
        try {
            cfgLoader = new SimulatorConfigLoader();
            log.info("Configuration loaded successfully.");
        } catch (ConfigurationLoadException e) {
            GlobalExceptionHandler.handle("Startup - Config Load", e);
            return;
        }

        try {
            log.debug("Creating services...");

            HolidayCalendarService holidaySvc = new HolidayCalendarService(cfgLoader);
            ShockService shockSvc = new ShockService(cfgLoader);

            RateSimulator simulator = new RateSimulator(
                    cfgLoader,
                    holidaySvc,
                    shockSvc
            );
            log.debug("Rate simulator initialized.");

            int port = cfgLoader.currentPort();
            RatesTcpServer server = new RatesTcpServer(port);
            server.start();
            log.info("TCP server started on port {}", port);

            SimulatorProperties props = cfgLoader.currentSimulator();
            RateUpdateScheduler scheduler = new RateUpdateScheduler(
                    simulator,
                    server.subscriptions(),
                    props.getUpdateIntervalMillis(),
                    props.getMaxUpdates()
            );
            scheduler.start();
            log.info("Rate update scheduler started (interval={} ms, maxUpdates={}).",
                    props.getUpdateIntervalMillis(), props.getMaxUpdates());

            cfgLoader.addChangeListener(() -> {
                SimulatorProperties newProps = cfgLoader.currentSimulator();
                log.info("[CONFIG] Change detected. Reconfiguring scheduler with new settings...");
                scheduler.reconfigure(newProps.getUpdateIntervalMillis(), newProps.getMaxUpdates());
                log.debug("Scheduler reconfigured to interval={} ms, maxUpdates={}",
                        newProps.getUpdateIntervalMillis(), newProps.getMaxUpdates());
            });

            log.info("Application started successfully and is now running.");
        } catch (Exception e) {
            GlobalExceptionHandler.handle("Startup - Initialization", e);
        }
    }
}