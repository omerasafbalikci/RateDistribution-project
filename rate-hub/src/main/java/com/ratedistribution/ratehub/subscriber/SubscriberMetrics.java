package com.ratedistribution.ratehub.subscriber;

public class SubscriberMetrics {
    private final String platform;
    private final boolean connected;
    private final long receivedCount;

    private SubscriberMetrics(String platform, boolean connected, long receivedCount) {
        this.platform = platform;
        this.connected = connected;
        this.receivedCount = receivedCount;
    }

    public static SubscriberMetrics of(String platform, boolean connected, long receivedCount) {
        return new SubscriberMetrics(platform, connected, receivedCount);
    }

    public String platform() {
        return platform;
    }

    public boolean connected() {
        return connected;
    }

    public long receivedCount() {
        return receivedCount;
    }

    @Override
    public String toString() {
        return String.format("SubscriberMetrics[platform=%s, connected=%s, received=%d]",
                platform, connected, receivedCount);
    }
}
