package com.ratedistribution.tdp;

import com.ratedistribution.tdp.config.SimulatorProperties;
import com.ratedistribution.tdp.net.SubscriptionManager;
import com.ratedistribution.tdp.net.TcpServer;
import com.ratedistribution.tdp.service.HolidayCalendarService;
import com.ratedistribution.tdp.service.RateSimulator;
import com.ratedistribution.tdp.utilities.MapperUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class TcpDataProviderApplication {
    public static void main(String[] args) {
        System.out.println("=== Starting TCP Data Provider ===");

        // 1) YAML den config yükle
        SimulatorProperties props = loadSimulatorProperties("application.yml");
        System.out.println("Configuration loaded. updateIntervalMillis="
                + props.getUpdateIntervalMillis()
                + ", maxUpdates="
                + props.getMaxUpdates());

        int port = 8084; // fallback
        System.out.println("TCP Server will start on port: " + port);

        // 2) Holiday servisi
        HolidayCalendarService holidayService = new HolidayCalendarService(props);

        // 3) Rate simulator
        RateSimulator simulator = new RateSimulator(props, holidayService);
        System.out.println("Rate simulator created.");

        // 4) SubscriptionManager + TCP Server
        SubscriptionManager subscriptionManager = new SubscriptionManager();

        try (TcpServer server = new TcpServer(port, subscriptionManager)) {
            server.startServer();
            System.out.println("TCP server started. Listening for clients...");

            // 5) UpdateScheduler
            Thread schedulerThread = new Thread(() -> {
                long interval = props.getUpdateIntervalMillis();
                int max = props.getMaxUpdates();
                int count = 0;
                while (max <= 0 || count < max) {
                    try {
                        List<String> updates = simulator.updateAllRates();
                        subscriptionManager.broadcastUpdates(updates);
                        count++;
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("Scheduler finished after " + count + " updates.");
            }, "RateUpdateScheduler");
            schedulerThread.start();

            // Program kapanana dek bekle
            schedulerThread.join();
            System.out.println("Scheduler thread joined. Program is ending...");
        } catch (Exception e) {
            // Hataları yutmak yerine ekranda gösterelim
            System.err.println("ERROR in main:");
            e.printStackTrace();
        }
        System.out.println("=== TCP Data Provider Terminated ===");
    }

    private static SimulatorProperties loadSimulatorProperties(String resourceName) {
        // SnakeYAML ile okuma
        try (InputStream in = TcpDataProviderApplication.class.getResourceAsStream("/" + resourceName)) {
            if (in == null) {
                throw new RuntimeException("Cannot find " + resourceName + " on classpath");
            }
            Yaml yaml = new Yaml();
            Object data = yaml.load(in);
            if (data instanceof Map<?,?> map) {
                return parseSimulatorProps(map);
            }
            throw new RuntimeException("Invalid YAML in " + resourceName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + resourceName, e);
        }
    }

    // Basit bir mapper, normalde Jackson/Spring Boot otomatik yapar
    @SuppressWarnings("unchecked")
    private static SimulatorProperties parseSimulatorProps(java.util.Map<?,?> map) {
        SimulatorProperties sp = new SimulatorProperties();

        Object simObj = map.get("simulator");
        if (simObj instanceof java.util.Map<?,?> simMap) {
            sp.setUpdateIntervalMillis(longVal(simMap.get("update-interval-millis")));
            sp.setMaxUpdates(intVal(simMap.get("max-updates")));

            Object corrMatrixObj = simMap.get("correlationMatrix");
            if (corrMatrixObj instanceof java.util.List<?> corrMatrixList) {
                sp.setCorrelationMatrix((java.util.List<java.util.List<Double>>) corrMatrixList);
            } else {
                sp.setCorrelationMatrix(java.util.List.of());
            }



            // rates
            Object ratesObj = simMap.get("rates");
            if (ratesObj instanceof java.util.List<?> list) {
                sp.setRates(MapperUtil.parseRatesList(list));
            }

            // sessionVolFactors
            Object sessionObj = simMap.get("sessionVolFactors");
            if(sessionObj instanceof java.util.List<?> sList) {
                sp.setSessionVolFactors(MapperUtil.parseSessionFactors(sList));
            }

            // holidays
            Object holObj = simMap.get("holidays");
            if(holObj instanceof java.util.List<?> hList) {
                sp.setHolidays(MapperUtil.parseHolidays(hList));
            }

            // weekendHandling
            Object whObj = simMap.get("weekendHandling");
            if(whObj instanceof java.util.Map<?,?> whMap) {
                sp.setWeekendHandling(MapperUtil.parseWeekendHandling(whMap));
            }

            // regime switching
            sp.setEnableRegimeSwitching(boolVal(simMap.get("enableRegimeSwitching")));
            Object lowVolObj = simMap.get("regimeLowVol");
            if(lowVolObj instanceof java.util.Map<?,?> lvMap) {
                sp.setRegimeLowVol(MapperUtil.parseRegimeDef(lvMap));
            }
            Object highVolObj = simMap.get("regimeHighVol");
            if(highVolObj instanceof java.util.Map<?,?> hvMap) {
                sp.setRegimeHighVol(MapperUtil.parseRegimeDef(hvMap));
            }
        }

        return sp;
    }

    private static long longVal(Object o) {
        if(o instanceof Number n) return n.longValue();
        return 0L;
    }
    private static int intVal(Object o) {
        if(o instanceof Number n) return n.intValue();
        return 0;
    }
    private static boolean boolVal(Object o) {
        if(o instanceof Boolean b) return b;
        return false;
    }
}