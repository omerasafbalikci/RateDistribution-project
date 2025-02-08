package com.ratedistribution.tdp.net;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionManager {
    // rateName -> list of active subscribers
    private final ConcurrentHashMap<String, List<PrintWriter>> subscriptions = new ConcurrentHashMap<>();

    public void subscribe(String rateName, PrintWriter out) {
        subscriptions.computeIfAbsent(rateName, r -> new ArrayList<>()).add(out);
        out.println("Subscribed to " + rateName);
        out.flush();
    }

    public void unsubscribe(String rateName, PrintWriter out) {
        List<PrintWriter> list = subscriptions.get(rateName);
        if (list != null) {
            list.remove(out);
            out.println("Unsubscribed from " + rateName);
            out.flush();
        }
    }

    public void broadcastUpdates(List<String> lines) {
        // lines = ["PF1_USDTRY|22:number:34.XXXX|25:number:35.XXXX|5:timestamp:2024-12-15T..."]
        for (String line : lines) {
            // rateName'i ayıkla
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2) continue;
            String rateName = parts[0];
            List<PrintWriter> outs = subscriptions.get(rateName);
            if (outs != null) {
                for (PrintWriter pw : outs) {
                    pw.println(line);
                    pw.flush();
                }
            }
        }
    }
}
