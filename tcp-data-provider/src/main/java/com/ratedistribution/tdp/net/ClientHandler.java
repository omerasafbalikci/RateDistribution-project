package com.ratedistribution.tdp.net;

import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable{
    private final Socket socket;
    private final SubscriptionManager subscriptionManager;

    public ClientHandler(Socket socket, SubscriptionManager subscriptionManager) {
        this.socket = socket;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("subscribe|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        subscriptionManager.subscribe(parts[1], out);
                    } else {
                        out.println("ERROR|Invalid request format");
                    }
                } else if (line.startsWith("unsubscribe|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        subscriptionManager.unsubscribe(parts[1], out);
                    } else {
                        out.println("ERROR|Invalid request format");
                    }
                } else {
                    out.println("ERROR|Invalid request format");
                }
            }
        } catch (IOException e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
