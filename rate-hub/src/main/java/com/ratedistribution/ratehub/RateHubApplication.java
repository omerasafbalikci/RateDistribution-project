package com.ratedistribution.ratehub;

import com.ratedistribution.ratehub.cache.HazelcastFactory;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.service.Coordinator;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class RateHubApplication {
    public static void main(String[] args) throws Exception {
        final Logger log = LogManager.getLogger(RateHubApplication.class);

        Path cfgPath = Path.of(System.getProperty("conf", "application.yml"));
        CoordinatorConfig cfg = CoordinatorConfig.load(cfgPath);

        var hz = HazelcastFactory.start(cfg.hazelcast().clusterName());
        var producer = new RateKafkaProducer(cfg.kafka().bootstrapServers(), cfg.kafka().topic());
        var coord = new Coordinator(hz, producer, cfg.toCalcDefs(), cfg.threadPool().size());
        var loader = new SubscriberLoader(cfg.subscribers(), coord);

        coord.registerAndStart(loader.load());
        Runtime.getRuntime().addShutdownHook(new Thread(coord::shutdown));
        log.info("RateHub started – Ctrl+C to exit");
    }
}