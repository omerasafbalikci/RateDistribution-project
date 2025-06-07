package com.ratedistribution.tdp.net;

import com.ratedistribution.tdp.advice.GlobalExceptionHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages client subscriptions for rate symbols.
 * Supports subscribing, unsubscribing, broadcasting updates,
 * and cleaning up disconnected sessions.
 * Uses a circuit breaker to protect the broadcast mechanism.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class SubscriptionManager {
    private static final Logger log = LogManager.getLogger(SubscriptionManager.class);
    private final ConcurrentHashMap<String, Set<ClientSession>> map = new ConcurrentHashMap<>();

    /**
     * Subscribes a client to a specific rate symbol.
     *
     * @param sym The symbol to subscribe to.
     * @param cs  The client session requesting the subscription.
     */
    public void subscribe(String sym, ClientSession cs) {
        String key = sym.toUpperCase(Locale.ROOT);
        map.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(cs);
        cs.push("Subscribed to " + key);
        log.info("Client {} subscribed to {}", cs.getSocket().getRemoteSocketAddress(), key);
    }

    /**
     * Unsubscribes a client from a specific rate symbol.
     *
     * @param sym The symbol to unsubscribe from.
     * @param cs  The client session requesting the unsubscription.
     */
    public void unsubscribe(String sym, ClientSession cs) {
        String key = sym.toUpperCase(Locale.ROOT);
        Set<ClientSession> sessions = map.get(key);
        if (sessions == null) {
            cs.push("ERROR|Rate not found: " + key);
            log.warn("Unsubscribe failed: rate {} not found for client {}", key, cs.getSocket().getRemoteSocketAddress());
            return;
        }
        if (sessions.remove(cs)) {
            cs.push("Unsubscribed from " + key);
            log.info("Client {} unsubscribed from {}", cs.getSocket().getRemoteSocketAddress(), key);
        } else {
            cs.push("ERROR|You are not subscribed to: " + key);
            log.warn("Client {} tried to unsubscribe from {} but was not subscribed", cs.getSocket().getRemoteSocketAddress(), key);
        }
    }

    /**
     * Broadcasts a JSON message to all clients subscribed to a symbol.
     * Protected by a circuit breaker to prevent overload.
     *
     * @param sym  The symbol to broadcast for.
     * @param message The JSON message to send.
     */
    public void broadcast(String sym, Object message) {
        String key = sym.toUpperCase(Locale.ROOT);
        Set<ClientSession> sessions = map.get(key);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No subscribers for {}. Skipping broadcast.", key);
            return;
        }

        Runnable push = () -> sessions.forEach(cs -> {
            try {
                cs.push(message);
            } catch (Exception e) {
                GlobalExceptionHandler.handle("Broadcast to " + key, e);
                log.warn("Failed to push update to client {}", cs.getSocket().getRemoteSocketAddress());
            }
        });

        try {
            Runnable decorated = CircuitBreaker.decorateRunnable(Breakers.SUBSCRIPTION, push);
            decorated.run();
            log.trace("Broadcasted update for {}", key);
        } catch (Exception ex) {
            log.error("[CB] Broadcast blocked for {}: {}", key, ex.getMessage(), ex);
        }
    }

    /**
     * Removes a client from all subscriptions.
     *
     * @param cs The client session to purge.
     */
    public void purge(ClientSession cs) {
        map.values().forEach(set -> set.remove(cs));
    }

    /**
     * Closes all active sessions and clears the subscription map.
     */
    public void cleanup() {
        map.values().forEach(sessions -> sessions.forEach(ClientSession::close));
        map.clear();
        log.info("Subscription manager cleaned up all sessions.");
    }
}
