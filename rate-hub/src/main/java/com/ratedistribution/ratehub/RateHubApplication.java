package com.ratedistribution.ratehub;

import com.ratedistribution.ratehub.cache.HazelcastFactory;
import com.ratedistribution.ratehub.config.AppConfigLoader;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.kafka.RateKafkaProducer;
import com.ratedistribution.ratehub.service.Coordinator;
import com.ratedistribution.ratehub.subscriber.Subscriber;
import com.ratedistribution.ratehub.subscriber.SubscriberLoader;

import java.nio.file.Path;
import java.util.List;

public class RateHubApplication {
    public static void main(String[] args) {
        Path conf = Path.of(System.getProperty("conf", "application.yml"));
        CoordinatorConfig cfg = AppConfigLoader.load(conf);
        var hz = HazelcastFactory.start(cfg.hazelcast().clusterName());
        var kafka = new RateKafkaProducer(cfg.kafka().bootstrapServers(), cfg.kafka().topic());
        Coordinator coord = new Coordinator(hz, kafka, cfg.toCalcDefs(), cfg.threadPool().size());
        SubscriberLoader loader = new SubscriberLoader(cfg.subscribers(), coord);
        List<Subscriber> subs = loader.loadAll();
        // register subscriptions as per YML
        subs.forEach(s -> {
            cfg.subscribers().stream().filter(c -> c.name().equals(s.name())).findFirst().ifPresent(c -> c.rates().forEach(s::subscribe));
        });
        coord.registerAndStart(subs);
        Runtime.getRuntime().addShutdownHook(new Thread(coord::shutdown));
        log.info("ForexCoordinatorApp started – press Ctrl+C to stop");

    }
}