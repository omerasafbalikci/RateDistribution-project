package com.ratedistribution.ratehub.subscriber;

import java.util.Set;

public interface Subscriber extends AutoCloseable, Runnable {
    void connect(String user, String pwd) throws Exception;

    void disconnect();

    void subscribe(String rateName);

    void unsubscribe(String rateName);

    Set<String> getSubscribedRates();

    String name();

    boolean isConnected();

    boolean healthCheck();

    SubscriberMetrics metrics();
}
