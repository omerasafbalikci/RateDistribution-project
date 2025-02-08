package com.ratedistribution.tdp.net;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer implements AutoCloseable {
    private final int port;
    private final SubscriptionManager subscriptionManager;
    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public TcpServer(int port, SubscriptionManager subscriptionManager) {
        this.port = port;
        this.subscriptionManager = subscriptionManager;
    }

    public void startServer() throws IOException {
        serverSocket = new ServerSocket(port);

        Thread acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    executor.submit(new ClientHandler(client, subscriptionManager));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                    }
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @Override
    public void close() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        executor.shutdownNow();
    }
}
