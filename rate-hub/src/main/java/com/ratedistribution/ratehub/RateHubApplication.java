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
import com.ratedistribution.ratehub.subscriber.Subscriber;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;
import com.ratedistribution.ratehub.utilities.MailService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

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
            String configArg = Stream.concat(
                            Arrays.stream(args),
                            Stream.of(System.getProperty("config", ""))
                    )
                    .filter(s -> s.startsWith("--config=") || s.startsWith("config="))
                    .findFirst()
                    .orElse("config=rate-hub/config/application-docker.yml");

            Path cfgPath = Paths.get(
                    configArg.replace("--config=", "")
                            .replace("config=", "")
            );
            log.info("[RateHub] Loading configuration from: {}", cfgPath.toAbsolutePath());

            CoordinatorConfig config = AppConfigLoader.load(cfgPath);
            log.info("[RateHub] Configuration loaded successfully.");

            HazelcastInstance hz = HazelcastFactory.start(config.hazelcast().clusterName());
            log.info("[RateHub] Hazelcast started: {}", config.hazelcast().clusterName());

            var producer = new RateKafkaProducer(
                    config.kafka().bootstrapServers(),
                    config.kafka().rawTopic(),
                    config.kafka().calcTopic()
            );
            var mailService = new MailService(config.mail());

            var tokenProvider = new TokenProvider(
                    config.auth().url(),
                    config.auth().username(),
                    config.auth().password(),
                    config.auth().refreshSkewSeconds()
            );

            Coordinator coordinator = new Coordinator(
                    hz,
                    producer,
                    config.subscribers(),
                    tokenProvider,
                    config.toDefs(),
                    config.threadPool().size(),
                    mailService
            );

            AppConfigReloader reloader = new AppConfigReloader(cfgPath, config);
            reloader.addListener(coordinator);
            reloader.startWatching();

            List<Subscriber> subscribers = new SubscriberLoader(
                    config.subscribers(),
                    coordinator,
                    tokenProvider
            ).load();
            coordinator.start(subscribers);
            log.info("[RateHub] Coordinator initialized and started.");

            Runtime.getRuntime().addShutdownHook(new Thread(coordinator::shutdown));

        } catch (Exception e) {
            log.error("[RateHub] Fatal error: {}", e.getMessage(), e);
            GlobalExceptionHandler.fatal("Fatal error during startup", e);
        }
    }
}