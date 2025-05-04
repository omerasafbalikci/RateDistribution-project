package com.ratedistribution.tdp.net;

import com.ratedistribution.common.JwtValidator;
import com.ratedistribution.tdp.advice.GlobalExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCP server that accepts client connections, creates sessions,
 * and manages subscription-based communication.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RatesTcpServer {
    private static final Logger log = LogManager.getLogger(RatesTcpServer.class);
    private final int port;
    private final JwtValidator jwtValidator;
    private ServerSocket serverSocket;
    private final SubscriptionManager subs = new SubscriptionManager();

    /**
     * Constructs the TCP server with specified port and JWT validator.
     *
     * @param port TCP port to listen on
     * @param jwtValidator validator for client tokens
     */
    public RatesTcpServer(int port, JwtValidator jwtValidator) {
        this.port = port;
        this.jwtValidator = jwtValidator;
    }

    /**
     * Starts the TCP server and begins accepting client connections.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            new Thread(this::acceptLoop, "tcp-acceptor").start();
            log.info("TCP server initialized on port {}", port);
        } catch (IOException e) {
            GlobalExceptionHandler.handle("RatesTcpServer.start", e);
            log.fatal("Unable to start TCP server on port {}", port);
        }
    }

    /**
     * Accepts incoming client connections and creates sessions.
     */
    private void acceptLoop() {
        log.info("Accept loop started. Waiting for client connections...");
        try {
            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    log.info("Accepted new connection from {}", client.getRemoteSocketAddress());
                    new ClientSession(client, subs, jwtValidator).start();
                } catch (Exception e) {
                    GlobalExceptionHandler.handle("Accept client session", e);
                }
            }
        } catch (Exception e) {
            GlobalExceptionHandler.handle("TCP Accept Loop", e);
        }
    }

    /**
     * Returns the subscription manager instance.
     *
     * @return active subscription manager
     */
    public SubscriptionManager subscriptions() {
        return subs;
    }

    /**
     * Stops the TCP server and cleans up all active sessions.
     */
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                log.info("Stopping TCP server...");
                serverSocket.close();
            }
            subs.cleanup();
            log.info("TCP server stopped successfully.");
        } catch (IOException e) {
            GlobalExceptionHandler.handle("RatesTcpServer.stop", e);
            log.error("Error while stopping TCP server: {}", e.getMessage());
        }
    }
}
