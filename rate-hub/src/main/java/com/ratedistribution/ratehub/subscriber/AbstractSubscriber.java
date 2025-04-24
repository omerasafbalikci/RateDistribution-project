package com.ratedistribution.ratehub.subscriber;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public abstract class AbstractSubscriber implements Subscriber, Runnable {
    protected final RateListener listener;
    protected final String platform;
    protected final Set<String> subs = ConcurrentHashMap.newKeySet();
    protected volatile boolean running = false;
    protected volatile Instant connectedAt;
    protected final AtomicLong received = new AtomicLong();
    private Thread thread;

    @Override
    public void connect(String user, String pwd) throws Exception {
        if (running) return;
        running = true;
        thread = Thread.startVirtualThread(this::run);
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
    public Optional<Instant> getConnectionTime() {
        return Optional.ofNullable(connectedAt);
    }

    @Override
    public long receivedCount() {
        return received.get();
    }

    @Override
    public void reset() {
        disconnect();
        subs.clear();
        received.set(0);
    }

    @Override
    public String status() {
        return String.format("Platform=%s, Connected=%s, Rates=%s, Received=%d",
                platform, running, subs.size(), received.get());
    }

    public abstract void run();
}
