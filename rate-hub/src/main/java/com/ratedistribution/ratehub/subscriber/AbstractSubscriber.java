package com.ratedistribution.ratehub.subscriber;

import com.ratedistribution.ratehub.coord.RateListener;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public abstract class AbstractSubscriber implements Subscriber {
    protected final RateListener listener;
    protected final String platform;
    protected final Set<String> subs = ConcurrentHashMap.newKeySet();
    protected volatile boolean running;
    protected volatile Instant connectedAt;
    protected final AtomicLong received = new AtomicLong();
    protected final AtomicReference<Instant> lastReceived = new AtomicReference<>();
    private Thread thread;

    @Override
    public void connect(String user, String pwd) {
        if (running) return;
        running = true;
        thread = Thread.startVirtualThread(() -> {
            try {
                run();
            } catch (Exception e) {
                listener.onRateError(platform, "RUN", e);
            }
        });
        connectedAt = Instant.now();
        listener.onConnect(platform, true);
    }

    @Override
    public void disconnect() {
        running = false;
        if (thread != null) thread.interrupt();
        listener.onDisconnect(platform, true);
    }

    @Override
    public void close() {
        disconnect();
    }

    @Override
    public void subscribe(String rateName) {
        subs.add(rateName);
    }

    @Override
    public void unsubscribe(String rateName) {
        subs.remove(rateName);
    }

    @Override
    public Set<String> getSubscribedRates() {
        return Collections.unmodifiableSet(subs);
    }

    @Override
    public String name() {
        return platform;
    }

    @Override
    public boolean isConnected() {
        return running;
    }

    @Override
    public boolean healthCheck() {
        return running && lastReceived.get() != null &&
                lastReceived.get().isAfter(Instant.now().minusSeconds(10));
    }

    protected void markReceived() {
        received.incrementAndGet();
        lastReceived.set(Instant.now());
    }

    @Override
    public SubscriberMetrics metrics() {
        return SubscriberMetrics.of(platform, running, received.get());
    }
}
