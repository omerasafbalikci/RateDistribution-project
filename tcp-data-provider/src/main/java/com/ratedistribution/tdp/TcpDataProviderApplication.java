package com.ratedistribution.tdp;

import com.ratedistribution.tdp.config.SimulatorConfigLoader;
import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.net.RatesTcpServer;
import com.ratedistribution.tdp.service.HolidayCalendarService;
import com.ratedistribution.tdp.service.RateSimulator;
import com.ratedistribution.tdp.service.RateUpdateScheduler;
import com.ratedistribution.tdp.service.ShockService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;

public class TcpDataProviderApplication {
    private static final Logger log = LogManager.getLogger(TcpDataProviderApplication.class);

    public static void main(String[] args) throws Exception {
        SimulatorConfigLoader cfgLoader = new SimulatorConfigLoader();

        HolidayCalendarService holidaySvc = new HolidayCalendarService(cfgLoader);
        ShockService shockSvc = new ShockService(cfgLoader);
        RateSimulator simulator = new RateSimulator(
                cfgLoader,
                holidaySvc,
                shockSvc,
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        );

        RatesTcpServer server = new RatesTcpServer(cfgLoader.currentPort());
        server.start();

        RateUpdateScheduler scheduler = new RateUpdateScheduler(
                simulator,
                server.subscriptions(),
                cfgLoader.currentSimulator().getUpdateIntervalMillis(),
                cfgLoader.currentSimulator().getMaxUpdates()
        );
        scheduler.start();

        cfgLoader.addChangeListener(() -> {
            SimulatorProperties properties = cfgLoader.currentSimulator();
            System.out.println("[CONFIG] Change detected. Reconfiguring scheduler...");
            scheduler.reconfigure(properties.getUpdateIntervalMillis(), properties.getMaxUpdates());
        });
    }
}