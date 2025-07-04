package com.ratedistribution.ratehub;

import com.hazelcast.core.HazelcastInstance;
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
        Path configPath = parseConfigPath(args);
        log.info("Loading configuration from {}", configPath);

        CoordinatorConfig config = AppConfigLoader.load(configPath);

        HazelcastInstance hazelcast = HazelcastFactory.start(config.hazelcast().clusterName());
        log.info("Hazelcast started: {}", config.hazelcast().clusterName());

        RateKafkaProducer producer = new RateKafkaProducer(
                config.kafka().bootstrapServers(),
                config.kafka().rawTopic(),
                config.kafka().calcTopic()
        );
        MailService mailService = new MailService(config.mail());
        TokenProvider tokenProvider = new TokenProvider(
                config.auth().url(),
                config.auth().username(),
                config.auth().password(),
                config.auth().refreshSkewSeconds()
        );

        Coordinator coordinator = new Coordinator(
                hazelcast,
                producer,
                config.subscribers(),
                tokenProvider,
                config.toDefs(),
                config.threadPool().size(),
                mailService
        );

        AppConfigReloader reloader = new AppConfigReloader(configPath, config);
        reloader.registerListener(coordinator);
        reloader.start();

        List<Subscriber> subscribers = new SubscriberLoader(
                config.subscribers(),
                coordinator,
                tokenProvider
        ).load();
        coordinator.start(subscribers);

        Runtime.getRuntime().addShutdownHook(new Thread(coordinator::shutdown));
        log.info("RateHub initialized and running.");
    }

    private static Path parseConfigPath(String[] args) {
        return Stream.concat(Arrays.stream(args), Stream.of(System.getProperty("config", "")))
                .filter(s -> s.startsWith("--config=") || s.startsWith("config="))
                .findFirst()
                .map(s -> s.replaceFirst("^(--config=|config=)", ""))
                .map(Paths::get)
                .orElse(Paths.get("config/application-docker.yml"));
    }
}