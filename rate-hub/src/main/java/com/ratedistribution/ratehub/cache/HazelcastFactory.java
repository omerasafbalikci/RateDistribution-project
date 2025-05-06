package com.ratedistribution.ratehub.cache;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Factory class to initialize and configure a HazelcastInstance for caching.
 * Provides a centralized way to start a Hazelcast node with predefined map configurations.
 * This class is not meant to be instantiated.
 *
 * @author Ömer Asaf BALIKÇI
 */

public final class HazelcastFactory {
    private HazelcastFactory() {
    }

    /**
     * Starts a new HazelcastInstance with the given cluster name.
     * Configures "rawTicks" and "calcRates" maps with no TTL expiration.
     *
     * @param cluster the name of the Hazelcast cluster
     * @return a new HazelcastInstance configured with custom map settings
     */
    public static HazelcastInstance start(String cluster) {
        Config config = new Config();
        config.setClusterName(cluster);
        config.addMapConfig(new MapConfig("rawTicks").setTimeToLiveSeconds(0));
        config.addMapConfig(new MapConfig("calcRates").setTimeToLiveSeconds(0));
        return Hazelcast.newHazelcastInstance(config);
    }
}
