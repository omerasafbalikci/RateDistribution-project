package com.ratedistribution.ratehub.cache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public final class HazelcastFactory {
    private HazelcastFactory() {
    }

    public static HazelcastInstance start(String cluster) {
        Config config = new Config();
        config.setClusterName(cluster);
        config.addMapConfig(new MapConfig("rawTicks").setTimeToLiveSeconds(0));
        config.addMapConfig(new MapConfig("calcRates").setTimeToLiveSeconds(0));
        return Hazelcast.newHazelcastInstance(config);
    }
}
