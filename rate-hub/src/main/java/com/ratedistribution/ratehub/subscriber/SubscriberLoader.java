package com.ratedistribution.ratehub.subscriber;

import com.ratedistribution.ratehub.config.CoordinatorConfig;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class SubscriberLoader {
    private static final Logger log = LogManager.getLogger(SubscriberLoader.class);
    private final List<CoordinatorConfig.SubscriberCfg> cfgs;
    private final RateListener listener;

    public List<Subscriber> loadAll() {
        List<Subscriber> list = new ArrayList<>();

        for (var cfg : cfgs) {
            try {
                Class<?> clz = Class.forName(cfg.className());
                Constructor<?> ctor = clz.getDeclaredConstructor(RateListener.class, String.class, String.class, int.class);
                Subscriber subscriber = (Subscriber) ctor.newInstance(listener, cfg.name(), cfg.host(), cfg.port());
                list.add(subscriber);
            } catch (ReflectiveOperationException e) {
                log.error("Failed to load subscriber {}: {}", cfg.className(), e.getMessage(), e);
            }
        }

        return list;
    }
}
