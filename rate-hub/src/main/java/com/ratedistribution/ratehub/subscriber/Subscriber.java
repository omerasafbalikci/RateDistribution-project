package com.ratedistribution.ratehub.subscriber;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface Subscriber {
    void connect(String user, String pwd) throws Exception;

    void disconnect();

    void subscribe(String rateName);

    void unsubscribe(String rateName);

    Set<String> getSubscribedRates();

    String name();

    boolean isConnected();

    Optional<Instant> getConnectionTime();

    long receivedCount();

    void reset();

    String status();
}
