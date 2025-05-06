package com.ratedistribution.ratehub.subscriber;

import com.ratedistribution.ratehub.coord.RateListener;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AbstractSubscriber provides a base implementation for Subscriber interface.
 * It manages connection state, subscriptions, and metrics tracking.
 * Concrete subclasses must implement the {@code run()} method for data handling.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RequiredArgsConstructor
public abstract class AbstractSubscriber implements Subscriber {
    protected final RateListener listener;
    protected final String platform;
    protected final Set<String> subs = ConcurrentHashMap.newKeySet();
    protected volatile boolean running;
    protected volatile Instant connectedAt;
    protected final AtomicLong received = new AtomicLong();
    protected final AtomicReference<Instant> lastReceived = new AtomicReference<>();
    protected final AtomicReference<Instant> lastActivity = new AtomicReference<>();
    private Thread thread;

    /**
     * Starts the subscriber in a virtual thread and sets connection state.
     */
    @Override
    public void connect() {
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
        markActivity();
        listener.onConnect(platform, true);
    }

    /**
     * Stops the subscriber and interrupts the internal thread.
     */
    @Override
    public void disconnect() {
        running = false;
        if (thread != null) thread.interrupt();
        listener.onDisconnect(platform, true);
    }

    /**
     * Closes the subscriber gracefully.
     */
    @Override
    public void close() {
        disconnect();
    }

    /**
     * Subscribes to a specific rate symbol.
     *
     * @param rateName the symbol to subscribe to
     */
    @Override
    public void subscribe(String rateName) {
        subs.add(rateName);
    }

    /**
     * Unsubscribes from a specific rate symbol.
     *
     * @param rateName the symbol to unsubscribe from
     */
    @Override
    public void unsubscribe(String rateName) {
        subs.remove(rateName);
    }

    /**
     * Returns an unmodifiable view of current subscriptions.
     *
     * @return set of subscribed rate symbols
     */
    @Override
    public Set<String> getSubscribedRates() {
        return Collections.unmodifiableSet(subs);
    }

    /**
     * Returns the name of the platform for identification.
     *
     * @return platform name
     */
    @Override
    public String name() {
        return platform;
    }

    /**
     * Indicates whether the subscriber is currently connected.
     *
     * @return true if connected, false otherwise
     */
    @Override
    public boolean isConnected() {
        return running;
    }

    /**
     * Performs a basic health check based on last received tick time.
     *
     * @return true if tick received within last 10 seconds
     */
    @Override
    public boolean healthCheck() {
        return running && lastReceived.get() != null &&
                lastReceived.get().isAfter(Instant.now().minusSeconds(10));
    }

    /**
     * Checks if there was any activity (tick or otherwise) recently.
     *
     * @return true if recent activity exists
     */
    public boolean connectionHealthy() {
        return running && lastActivity.get() != null &&
                lastActivity.get().isAfter(Instant.now().minusSeconds(10));
    }

    /**
     * Marks the arrival of a new tick. Used to track health and metrics.
     */
    protected void markReceived() {
        received.incrementAndGet();
        lastReceived.set(Instant.now());
        markActivity();
    }

    /**
     * Updates the timestamp of last activity.
     */
    protected void markActivity() {
        lastActivity.set(Instant.now());
    }

    /**
     * Returns current runtime metrics of the subscriber.
     *
     * @return SubscriberMetrics instance
     */
    @Override
    public SubscriberMetrics metrics() {
        return SubscriberMetrics.of(platform, running, received.get());
    }
}
