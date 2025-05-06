package com.ratedistribution.ratehub;

import com.hazelcast.core.HazelcastInstance;
import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import com.ratedistribution.ratehub.auth.TokenProvider;
import com.ratedistribution.ratehub.cache.HazelcastFactory;
import com.ratedistribution.ratehub.config.AppConfigLoader;
import com.ratedistribution.ratehub.config.AppConfigReloader;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.coord.Coordinator;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;
import com.ratedistribution.ratehub.utilities.MailService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Main application entry point for the RateHub system.
 * Initializes configuration, Hazelcast cache, Kafka producer,
 * token-based authentication, subscriber loading, and
 * starts the Coordinator to manage real-time data flow.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RateHubApplication {
    private static final Logger log = LogManager.getLogger(RateHubApplication.class);

    public static void main(String[] args) {
        try {
            Path cfgPath = Path.of(System.getProperty("conf", "rate-hub/config/application.yml"));
            log.info("[RateHub] Loading configuration from: {}", cfgPath.toAbsolutePath());

            CoordinatorConfig config = AppConfigLoader.load(cfgPath);
            log.info("[RateHub] Configuration loaded successfully.");

            AppConfigReloader reloader = new AppConfigReloader(cfgPath, config);
            reloader.startWatching();

            var hazelcast = HazelcastFactory.start(config.hazelcast().clusterName());
            log.info("[RateHub] Hazelcast instance started with cluster name '{}'", config.hazelcast().clusterName());

            var coordinator = getCoordinator(reloader.getConfig(), hazelcast);
            log.info("[RateHub] Coordinator initialized and started.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("[RateHub] Shutdown signal received. Cleaning up...");
                coordinator.shutdown();
            }));
        } catch (Exception e) {
            log.error("[RateHub] Fatal error during startup: {}", e.getMessage(), e);
            GlobalExceptionHandler.fatal("Fatal error during startup: ", e);
        }
    }

    /**
     * Constructs and starts the Coordinator component with necessary dependencies.
     *
     * @param config    loaded application configuration
     * @param hazelcast Hazelcast instance for shared state
     * @return initialized and started {@link Coordinator}
     */
    private static Coordinator getCoordinator(CoordinatorConfig config, HazelcastInstance hazelcast) {
        log.info("[Coordinator] Initializing dependencies...");

        var producer = new RateKafkaProducer(
                config.kafka().bootstrapServers(),
                config.kafka().rawTopic(),
                config.kafka().calcTopic()
        );
        log.debug("[Coordinator] Kafka producer initialized.");

        var mailService = new MailService(config.mail());
        log.debug("[Coordinator] Mail service initialized.");

        var coordinator = new Coordinator(hazelcast, producer, config.toDefs(), config.threadPool().size(), mailService);
        log.debug("[Coordinator] Coordinator instance created.");

        var tp = new TokenProvider(
                config.auth().url(),
                config.auth().username(),
                config.auth().password(),
                config.auth().refreshSkewSeconds());
        log.debug("[Coordinator] Token provider initialized.");

        var loader = new SubscriberLoader(config.subscribers(), coordinator, tp);
        var subscribers = loader.load();
        log.info("[Coordinator] Loaded {} subscriber(s).", subscribers.size());

        coordinator.start(subscribers);
        log.info("[Coordinator] All subscribers connected and processing started.");
        return coordinator;
    }
}