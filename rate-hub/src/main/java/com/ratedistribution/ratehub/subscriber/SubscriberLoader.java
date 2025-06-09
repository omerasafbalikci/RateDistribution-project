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

/**
 * SubscriberLoader dynamically loads and initializes subscriber instances (e.g., TCP or REST)
 * based on the provided subscriber configurations.
 * Supports reflection-based instantiation and assigns subscriptions from config.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RequiredArgsConstructor
public class SubscriberLoader {
    private static final Logger log = LogManager.getLogger(SubscriberLoader.class);
    private final List<CoordinatorConfig.SubscriberCfg> configs;
    private final RateListener listener;
    private final TokenProvider tokenProvider;

    /**
     * Loads all subscriber instances based on the provided configurations.
     *
     * @return a list of successfully initialized Subscriber instances
     */
    public List<Subscriber> load() {
        log.info("[SubscriberLoader] Starting subscriber loading process...");
        List<Subscriber> subscribers = new ArrayList<>();
        for (var cfg : configs) {
            log.debug("[SubscriberLoader] Processing subscriber config: {}", cfg.name());
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
                    log.debug("[SubscriberLoader] Instantiating TcpSubscriber for {}", cfg.name());
                    var ctor = clazz.getConstructor(RateListener.class, String.class,
                            String.class, int.class, TokenProvider.class);
                    subscriber = (Subscriber) ctor.newInstance(listener, cfg.name(),
                            cfg.host(), cfg.port(), tokenProvider);
                } else if (clazz == RestSubscriber.class) {
                    log.debug("[SubscriberLoader] Instantiating RestSubscriber for {}", cfg.name());
                    var ctor = clazz.getConstructor(RateListener.class, String.class, String.class, TokenProvider.class);
                    String baseUrl = cfg.host().endsWith("/")
                            ? cfg.host().substring(0, cfg.host().length() - 1)
                            : cfg.host();
                    if (cfg.port() > 0) {
                        baseUrl = baseUrl + ":" + cfg.port();
                    }

                    subscriber = (Subscriber) ctor.newInstance(
                            listener, cfg.name(), baseUrl, tokenProvider);
                } else {
                    log.error("[SubscriberLoader] Unsupported subscriber type: {}", cfg.className());
                    continue;
                }

                if (cfg.rates() != null) {
                    cfg.rates().forEach(subscriber::subscribe);
                    log.debug("[SubscriberLoader] Subscribed to rates: {}", cfg.rates());
                } else {
                    log.warn("[SubscriberLoader] Subscriber {} has no rates to subscribe", cfg.name());
                }

                subscribers.add(subscriber);
                log.info("[SubscriberLoader] Loaded subscriber: {}", cfg.name());
            } catch (Exception e) {
                log.error("[SubscriberLoader] Failed to load subscriber {}: {}", cfg.name(), e.getMessage(), e);
            }
        }
        log.info("[SubscriberLoader] Loaded {} subscriber(s) successfully.", subscribers.size());
        return subscribers;
    }

    /**
     * Validates the essential fields of a SubscriberCfg.
     *
     * @param cfg the configuration object to validate
     * @return true if config is valid, false otherwise
     */
    private boolean validateConfig(CoordinatorConfig.SubscriberCfg cfg) {
        boolean valid = cfg.className() != null && !cfg.className().isBlank()
                && cfg.name() != null && !cfg.name().isBlank()
                && cfg.host() != null && !cfg.host().isBlank()
                && cfg.port() >= 0;

        if (!valid) {
            log.warn("[SubscriberLoader] Configuration validation failed for {}", cfg.name());
        }

        return valid;
    }
}
