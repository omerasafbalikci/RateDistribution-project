package com.ratedistribution.tdp.net;

import com.ratedistribution.common.JwtValidator;
import com.ratedistribution.tdp.advice.GlobalExceptionHandler;
import com.ratedistribution.tdp.utilities.serializer.JsonUtils;
import io.jsonwebtoken.Claims;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Manages a single client session for the TCP server.
 * Handles authentication, subscription commands, and message delivery.
 *
 * @author Ömer Asaf BALIKÇI
 */

@RequiredArgsConstructor
@Data
public class ClientSession implements Runnable {
    private static final Logger log = LogManager.getLogger(ClientSession.class);
    private final Socket socket;
    private final SubscriptionManager subs;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final JwtValidator jwtValidator;
    private boolean authenticated = false;
    private List<String> roles = List.of();
    private int failedAuthAttempts = 0;
    private static final int MAX_FAILED_ATTEMPTS = 3;

    /**
     * Alternative constructor that initializes I/O streams.
     *
     * @param s         Socket for client connection
     * @param subs      Subscription manager instance
     * @param validator JWT validator for authentication
     * @throws IOException if socket stream setup fails
     */
    ClientSession(Socket s, SubscriptionManager subs, JwtValidator validator) throws IOException {
        this.socket = s;
        this.subs = subs;
        this.jwtValidator = validator;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Starts the client session in a new thread.
     */
    void start() {
        new Thread(this, "client-" + socket.getPort()).start();
    }

    /**
     * Handles incoming commands from the client.
     * Applies authentication and command-based routing.
     */
    @Override
    public void run() {
        try {
            log.debug("New client connected from {}", socket.getRemoteSocketAddress());
            send("WELCOME|Connected to Rate TCP Server");
            send("Escape character is '^]'");
            String l;
            while ((l = in.readLine()) != null) {
                handle(normalizeBackspace(l.trim()));
            }
        } catch (IOException e) {
            GlobalExceptionHandler.handle("ClientSession.run (read or send error)", e);
            log.warn("Client {} disconnected unexpectedly: {}", socket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Parses and executes the received command.
     *
     * @param cmd Client command string
     */
    private void handle(String cmd) {
        try {
            if (cmd == null || cmd.isBlank() || !cmd.contains("|")) {
                send("ERROR|Invalid request format");
                return;
            }
            String[] parts = cmd.split("\\|", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                send("ERROR|Invalid request format");
                return;
            }

            String op = parts[0].toUpperCase(Locale.ROOT);
            String arg = parts[1];

            switch (op) {
                case "AUTH" -> authenticate(arg);
                case "SUBSCRIBE", "UNSUBSCRIBE" -> authorized(op, arg);
                default -> {
                    send("ERROR|Unknown command");
                    log.warn("Client {} sent unknown command: {}", socket.getRemoteSocketAddress(), op);
                }
            }
        } catch (Exception e) {
            GlobalExceptionHandler.handle("ClientSession.handle", e);
            send("ERROR|Internal server error");
        }
    }

    /**
     * Authenticates the user based on JWT token.
     *
     * @param token JWT token string
     */
    private void authenticate(String token) {
        try {
            Claims c = jwtValidator.getClaimsAndValidate(token);
            if (jwtValidator.isLoggedOut(token)) {
                send("ERROR|TOKEN_LOGGED_OUT");
                log.info("Client {} attempted login with logged-out token", socket.getRemoteSocketAddress());
                close();
                return;
            }

            roles = jwtValidator.getRoles(c);
            authenticated = true;
            send("OK|AUTHENTICATED");
            log.info("Client {} authenticated successfully with roles {}", socket.getRemoteSocketAddress(), roles);
        } catch (Exception e) {
            failedAuthAttempts++;
            send("AUTH_ERROR|INVALID_TOKEN");
            log.warn("Client {} failed authentication attempt {}: {}", socket.getRemoteSocketAddress(), failedAuthAttempts, e.getMessage());
            if (failedAuthAttempts >= MAX_FAILED_ATTEMPTS) {
                send("AUTH_ERROR|TOO_MANY_ATTEMPTS");
                log.warn("Client {} reached max failed auth attempts. Closing connection.", socket.getRemoteSocketAddress());
                close();
            }
        }
    }

    /**
     * Handles authorized commands (SUBSCRIBE/UNSUBSCRIBE) if user is authenticated.
     *
     * @param op  Operation name
     * @param sym Target symbol
     */
    private void authorized(String op, String sym) {
        if (!authenticated) {
            send("AUTH_ERROR|AUTHENTICATION_REQUIRED");
            return;
        }

        boolean allowed = roles.stream().anyMatch(r ->
                r.equals("ADMIN") || r.equals("OPERATOR"));

        if (!allowed) {
            send("AUTH_ERROR|INSUFFICIENT_PERMISSIONS");
            return;
        }

        switch (op) {
            case "SUBSCRIBE" -> subs.subscribe(sym, this);
            case "UNSUBSCRIBE" -> subs.unsubscribe(sym, this);
        }
    }

    /**
     * Sends a message to the client as a data push.
     *
     * @param payload JSON-encoded message
     */
    void push(Object payload) {
        try {
            String msg = (payload instanceof String)
                    ? (String) payload
                    : JsonUtils.toJson(payload);
            send(msg);
        } catch (Exception e) {
            log.warn("Failed to push to {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
            GlobalExceptionHandler.handle("ClientSession.push", e);
        }
    }

    /**
     * Sends a single message to the client.
     *
     * @param s Message string
     */
    private void send(String s) {
        try {
            out.write(s);
            out.write("\r\n");
            out.flush();
        } catch (IOException e) {
            log.error("Failed to send message to {}: {}", socket.getRemoteSocketAddress(), s);
            GlobalExceptionHandler.handle("ClientSession.send", e);
            close();
        }
    }

    /**
     * Closes the client connection and removes all subscriptions.
     */
    void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
                log.info("Closed connection with {}", socket.getRemoteSocketAddress());
            }
        } catch (IOException e) {
            log.warn("Failed to close socket for {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
            GlobalExceptionHandler.handle("ClientSession.close", e);
        }
        subs.purge(this);
    }

    /**
     * Removes characters affected by backspaces from client input.
     *
     * @param input Raw input
     * @return Normalized string
     */
    private String normalizeBackspace(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c == '\b') {
                if (!sb.isEmpty()) sb.deleteCharAt(sb.length() - 1);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
