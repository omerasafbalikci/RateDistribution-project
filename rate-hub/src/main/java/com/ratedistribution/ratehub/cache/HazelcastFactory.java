package com.ratedistribution.ratehub.cache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public final class HazelcastFactory {
    private HazelcastFactory() {
    }

    public static HazelcastInstance start(String cluster) {
        Config c = new Config();
        c.setClusterName(cluster);
        c.addMapConfig(new MapConfig("rawRates").setTimeToLiveSeconds(0));
        c.addMapConfig(new MapConfig("calcRates").setTimeToLiveSeconds(0));
        return Hazelcast.newHazelcastInstance(c);
    }
}
