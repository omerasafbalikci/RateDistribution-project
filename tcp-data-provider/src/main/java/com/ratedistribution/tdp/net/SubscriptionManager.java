package com.ratedistribution.tdp.net;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionManager {
    private final ConcurrentHashMap<String, Set<ClientSession>> map = new ConcurrentHashMap<>();

    public void subscribe(String sym, ClientSession cs) {
        String key = sym.toUpperCase(Locale.ROOT);
        map.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(cs);
        cs.push("Subscribed to " + key);
    }

    public void unsubscribe(String sym, ClientSession cs) {
        String key = sym.toUpperCase(Locale.ROOT);
        Set<ClientSession> sessions = map.get(key);
        if (sessions == null) {
            System.out.println("[SUBS] No such rate: " + key);
            cs.push("ERROR|Rate not found: " + key);
            return;
        }
        if (sessions.remove(cs)) {
            System.out.println("[SUBS] Unsubscribed from " + key);
            cs.push("Unsubscribed from " + key);
        } else {
            System.out.println("[SUBS] Not subscribed to " + key);
            cs.push("ERROR|You are not subscribed to: " + key);
        }
    }

    public void broadcast(String sym, String json) {
        String key = sym.toUpperCase(Locale.ROOT);
        Set<ClientSession> sessions = map.get(key);
        if (sessions == null || sessions.isEmpty()) {
            System.out.println("[SUBS] No subscribers for " + key);
            return;
        }
        System.out.println("[SUBS] Broadcasting to " + sessions.size() + " clients for " + key);
        sessions.forEach(cs -> cs.push(json));
    }

    public void purge(ClientSession cs) {
        map.values().forEach(set -> set.remove(cs));
    }
}
