package com.ratedistribution.tdp.net;

import java.io.IOException;
import java.net.ServerSocket;

public class RatesTcpServer {
    private final int port;
    private ServerSocket serverSocket;
    private final SubscriptionManager subs = new SubscriptionManager();

    public RatesTcpServer(int port) { this.port = port; }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        new Thread(this::acceptLoop, "tcp-acceptor").start();
    }
    private void acceptLoop() {
        try { while (!serverSocket.isClosed()) new ClientSession(serverSocket.accept(), subs).start(); }
        catch (IOException ignored) {}
    }
    public SubscriptionManager subscriptions() { return subs; }
    public void stop() throws IOException { serverSocket.close(); }
}
