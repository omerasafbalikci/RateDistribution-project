package com.ratedistribution.ratehub.subscriber;

import com.ratedistribution.ratehub.auth.TokenProvider;
import com.ratedistribution.ratehub.config.CoordinatorConfig;
import com.ratedistribution.ratehub.coord.RateListener;
import com.ratedistribution.ratehub.subscriber.impl.RestSubscriber;
import com.ratedistribution.ratehub.subscriber.impl.TcpSubscriber;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class SubscriberLoader {
    private static final Logger log = LogManager.getLogger(SubscriberLoader.class);
    private final List<CoordinatorConfig.SubscriberCfg> configs;
    private final RateListener listener;
    private final TokenProvider tokenProvider;

    public List<Subscriber> load() {
        List<Subscriber> subscribers = new ArrayList<>();
        for (var cfg : configs) {
            if (!validateConfig(cfg)) {
                log.error("[SubscriberLoader] Invalid configuration for subscriber: {}", cfg.name());
                continue;
            }
            try {
                Class<?> clazz = Class.forName(cfg.className());
                if (!Subscriber.class.isAssignableFrom(clazz)) {
                    log.error("[SubscriberLoader] Class {} does not implement Subscriber interface", cfg.className());
                    continue;
                }

                Subscriber subscriber;
                if (clazz == TcpSubscriber.class) {
                    var ctor = clazz.getConstructor(RateListener.class, String.class,
                            String.class, int.class, TokenProvider.class);
                    subscriber = (Subscriber) ctor.newInstance(listener, cfg.name(),
                            cfg.host(), cfg.port(), tokenProvider);
                } else if (clazz == RestSubscriber.class) {
                    var ctor = clazz.getConstructor(RateListener.class, String.class, String.class, TokenProvider.class);
                    subscriber = (Subscriber) ctor.newInstance(listener, cfg.name(),
                            cfg.host(), tokenProvider);
                } else {
                    log.error("[SubscriberLoader] Unsupported subscriber type {}", cfg.className());
                    continue;
                }

                if (cfg.rates() != null) {
                    cfg.rates().forEach(subscriber::subscribe);
                } else {
                    log.warn("[SubscriberLoader] Subscriber {} has no rates to subscribe", cfg.name());
                }

                subscribers.add(subscriber);
                log.info("[SubscriberLoader] Loaded subscriber: {}", cfg.name());
            } catch (Exception e) {
                log.error("[SubscriberLoader] Failed to load subscriber {}: {}", cfg.name(), e.getMessage(), e);
            }
        }

        return subscribers;
    }

    private boolean validateConfig(CoordinatorConfig.SubscriberCfg cfg) {
        return cfg.className() != null && !cfg.className().isBlank()
                && cfg.name() != null && !cfg.name().isBlank()
                && cfg.host() != null && !cfg.host().isBlank()
                && cfg.port() >= 0;
    }
}
