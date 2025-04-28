package com.ratedistribution.ratehub;

import com.ratedistribution.ratehub.cache.HazelcastFactory;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.coord.Coordinator;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class RateHubApplication {
    public static void main(String[] args) throws Exception {
        Logger log = LogManager.getLogger(RateHubApplication.class);

        Path cfgPath = Path.of(System.getProperty("conf", "application.yml"));
        CoordinatorConfig cfg = CoordinatorConfig.load(cfgPath);

        var hazelcast = HazelcastFactory.start(cfg.hazelcast().clusterName());
        var producer   = new RateKafkaProducer(cfg.kafka().bootstrapServers(), cfg.kafka().topic());
        var coordinator= new Coordinator(hazelcast, producer, cfg.toDefs(), cfg.threadPool().size());
        var loader     = new SubscriberLoader(cfg.subscribers(), coordinator);

        coordinator.start(loader.load());
        Runtime.getRuntime().addShutdownHook(new Thread(coordinator::shutdown));
        log.info("RateHub started – Ctrl+C to exit");
    }
}