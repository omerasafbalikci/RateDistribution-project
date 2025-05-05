package com.ratedistribution.ratehub;

import com.hazelcast.core.HazelcastInstance;
import com.ratedistribution.ratehub.auth.TokenProvider;
import com.ratedistribution.ratehub.cache.HazelcastFactory;
import com.ratedistribution.ratehub.config.AppConfigLoader;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.coord.Coordinator;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;
import com.ratedistribution.ratehub.utilities.MailService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class RateHubApplication {
    public static void main(String[] args) throws Exception {
        Logger log = LogManager.getLogger(RateHubApplication.class);


        try {
            // Konfigürasyon yolu
            Path cfgPath = Path.of(System.getProperty("conf", "config/application.yml"));
            log.info("Loading configuration from {}", cfgPath.toAbsolutePath());

            // Konfigürasyon yükle
            CoordinatorConfig config = AppConfigLoader.load(cfgPath);

            // Hazelcast başlat
            var hazelcast = HazelcastFactory.start(config.hazelcast().clusterName());

            var coordinator = getCoordinator(config, hazelcast);

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received. Cleaning up...");
                coordinator.shutdown();
            }));

            log.info("✅ RateHub started successfully – Ctrl+C to exit");

        } catch (Exception e) {
            log.error("❌ Failed to start RateHub: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static Coordinator getCoordinator(CoordinatorConfig config, HazelcastInstance hazelcast) {
        var producer = new RateKafkaProducer(
                config.kafka().bootstrapServers(),
                config.kafka().rawTopic(),
                config.kafka().calcTopic()
        );

        var mailService = new MailService(config.mail());

        // Coordinator başlat
        var coordinator = new Coordinator(hazelcast, producer, config.toDefs(), config.threadPool().size(), mailService);

        var tp = new TokenProvider(
                config.auth().url(),
                config.auth().username(),
                config.auth().password(),
                config.auth().refreshSkewSeconds());

        // Subscriber yükle
        var loader = new SubscriberLoader(config.subscribers(), coordinator, tp);
        var subscribers = loader.load();

        // Sistem başlasın
        coordinator.start(subscribers);
        return coordinator;
    }
}