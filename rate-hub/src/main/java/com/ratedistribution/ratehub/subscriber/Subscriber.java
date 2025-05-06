package com.ratedistribution.ratehub.subscriber;

import java.util.Set;

/**
 * Represents a generic rate data subscriber that can connect to a data source,
 * subscribe/unsubscribe to rates, and report health and metrics.
 * Implementations can use various protocols like REST, TCP, etc.
 *
 * @author Ömer Asaf BALIKÇI
 */

public interface Subscriber extends AutoCloseable, Runnable {
    /**
     * Connects to the remote data source and starts receiving data.
     *
     * @throws Exception if the connection fails
     */
    void connect() throws Exception;

    /**
     * Disconnects from the data source and stops receiving data.
     */
    void disconnect();

    /**
     * Subscribes to the given rate symbol.
     *
     * @param rateName the name of the rate to subscribe to
     */
    void subscribe(String rateName);

    /**
     * Unsubscribes from the given rate symbol.
     *
     * @param rateName the name of the rate to unsubscribe from
     */
    void unsubscribe(String rateName);

    /**
     * Returns the currently subscribed rate names.
     *
     * @return a set of subscribed rate symbols
     */
    Set<String> getSubscribedRates();

    /**
     * Returns the name of the platform or subscriber source.
     *
     * @return the platform name
     */
    String name();

    /**
     * Indicates whether the subscriber is currently connected.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Performs a health check to determine if the subscriber is active.
     *
     * @return true if healthy, false otherwise
     */
    boolean healthCheck();

    /**
     * Performs a connection-level health check.
     *
     * @return true if connection is healthy
     */
    default boolean connectionHealthy() {
        return healthCheck();
    }

    /**
     * Returns runtime metrics such as received count and connection state.
     *
     * @return the current SubscriberMetrics
     */
    SubscriberMetrics metrics();
}
