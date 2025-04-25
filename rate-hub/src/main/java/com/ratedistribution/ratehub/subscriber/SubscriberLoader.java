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

    public List<Subscriber> load() {
        List<Subscriber> list = new ArrayList<>();
        for (var c : cfgs) {
            try {
                Class<?> clz = Class.forName(c.className());
                Constructor<?> ctor = clz.getDeclaredConstructor(RateListener.class, String.class, String.class, int.class);
                Subscriber s = (Subscriber) ctor.newInstance(listener, c.name(), c.host(), c.port());
                c.rates().forEach(s::subscribe);
                list.add(s);
            } catch (Exception e) {
                log.error("load {}", c.className(), e);
            }
        }
        return list;
    }
}
