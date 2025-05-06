package com.ratedistribution.ratehub.subscriber;

/**
 * SubscriberMetrics represents runtime statistics of a {@link Subscriber} instance.
 * It includes platform identification, connection status, and the number of received messages.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class SubscriberMetrics {
    private final String platform;
    private final boolean connected;
    private final long receivedCount;

    /**
     * Private constructor to enforce usage of the factory method.
     *
     * @param platform      the name of the platform
     * @param connected     whether the subscriber is currently connected
     * @param receivedCount number of received ticks/messages
     */
    private SubscriberMetrics(String platform, boolean connected, long receivedCount) {
        this.platform = platform;
        this.connected = connected;
        this.receivedCount = receivedCount;
    }

    /**
     * Creates a new instance of SubscriberMetrics.
     *
     * @param platform      the platform name
     * @param connected     connection status
     * @param receivedCount total received message count
     * @return a new {@code SubscriberMetrics} instance
     */
    public static SubscriberMetrics of(String platform, boolean connected, long receivedCount) {
        return new SubscriberMetrics(platform, connected, receivedCount);
    }

    /**
     * Returns the name of the platform associated with the subscriber.
     *
     * @return the platform name
     */
    public String platform() {
        return platform;
    }

    /**
     * Indicates whether the subscriber is currently connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean connected() {
        return connected;
    }

    /**
     * Returns the total number of received messages by the subscriber.
     *
     * @return the received count
     */
    public long receivedCount() {
        return receivedCount;
    }

    /**
     * Returns a string representation of the metrics.
     *
     * @return formatted string with platform, connection status, and received count
     */
    @Override
    public String toString() {
        return String.format("SubscriberMetrics[platform=%s, connected=%s, received=%d]",
                platform, connected, receivedCount);
    }
}
