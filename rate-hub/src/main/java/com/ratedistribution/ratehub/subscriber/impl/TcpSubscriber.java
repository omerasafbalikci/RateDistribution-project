package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ratedistribution.ratehub.auth.TokenProvider;
import com.ratedistribution.ratehub.coord.RateListener;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.AbstractSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TcpSubscriber connects to a TCP server and listens for JSON-formatted RawTick data.
 * It supports token-based authentication and resubscription on reconnect.
 * Automatically retries on failures with exponential backoff.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class TcpSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(TcpSubscriber.class);
    private static final Pattern CMD = Pattern.compile("^[A-Z]+\\|.*");
    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private PrintWriter out;
    private final List<Long> backoffSeq = List.of(1000L, 2000L, 4000L, 8000L, 16000L, 32000L);
    private final TokenProvider tokenProvider;

    /**
     * Constructs a new TcpSubscriber.
     *
     * @param listener     listener to handle rate updates
     * @param platformName the name of the platform
     * @param host         TCP server host
     * @param port         TCP server port
     * @param tp           TokenProvider for authentication
     */
    public TcpSubscriber(RateListener listener, String platformName, String host, int port, TokenProvider tp) {
        super(listener, platformName);
        this.host = host;
        this.port = port;
        this.tokenProvider = tp;
    }

    /**
     * Subscribes to a rate symbol by sending a subscribe command.
     */
    @Override
    public void subscribe(String rateName) {
        log.debug("[TCP] Subscribing to {}", rateName);
        super.subscribe(rateName);
        sendCmd("subscribe|" + rateName);
    }

    /**
     * Unsubscribes from a rate symbol by sending an unsubscribe command.
     */
    @Override
    public void unsubscribe(String rateName) {
        log.debug("[TCP] Unsubscribing from {}", rateName);
        super.unsubscribe(rateName);
        sendCmd("unsubscribe|" + rateName);
    }

    /**
     * Main loop: connects to TCP, listens to messages, handles reconnection on failure.
     */
    @Override
    public void run() {
        int attempt = 0;
        log.info("[TCP] Starting TCP subscriber for {} at {}:{}", platform, host, port);
        while (running) {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                this.out = output;
                log.info("[TCP] Connected to {}:{}", host, port);

                authenticate();
                resubscribeAll();
                attempt = 0;

                String line;
                while (running && (line = in.readLine()) != null) {
                    markActivity();
                    if (line.startsWith("AUTH_ERROR|TOKEN_EXPIRED")) {
                        log.warn("[TCP] Token expired, re-authenticating...");
                        authenticate();
                        continue;
                    }

                    if (CMD.matcher(line).matches() || !line.startsWith("{")) {
                        log.trace("[TCP] Ignored line: {}", line);
                        continue;
                    }
                    process(line);
                }
                log.warn("[TCP] Disconnected from server {}:{}", host, port);
            } catch (Exception e) {
                listener.onRateError(platform, "TCP", e);
                long delay = backoffSeq.get(Math.min(attempt++, backoffSeq.size() - 1));
                log.warn("[TCP] Connection failed. Retrying in {} ms (attempt #{})", delay, attempt);
                sleep(delay);
            }
        }
        log.info("[TCP] TCP subscriber stopped for {}", platform);
    }

    /**
     * Sends resubscribe commands for all active rate symbols.
     */
    private void resubscribeAll() {
        log.debug("[TCP] Resubscribing to {} symbols", subs.size());
        subs.forEach(r -> sendCmd("subscribe|" + r));
    }

    /**
     * Sends authentication token to the server.
     */
    private void authenticate() {
        try {
            String token = tokenProvider.get();
            sendCmd("AUTH|" + token);
            log.debug("[TCP] Sent authentication token");
        } catch (Exception e) {
            log.error("[TCP] Failed to authenticate", e);
        }
    }

    /**
     * Parses a JSON tick message and forwards it to the listener.
     *
     * @param json JSON string representing a RawTick
     */
    private void process(String json) {
        try {
            RawTick raw = mapper.readValue(json, RawTick.class);
            if (!subs.contains(raw.rateName())) {
                log.trace("[TCP] Ignoring tick for unsubscribed rate: {}", raw.rateName());
                return;
            }
            markReceived();
            log.debug("[TCP] Received tick for {}: bid={}, ask={}", raw.rateName(), raw.bid(), raw.ask());
            listener.onRateAvailable(platform, raw.rateName(), raw);
            listener.onRateUpdate(platform, raw.rateName(), new RateFields(raw.bid(), raw.ask(), raw.timestamp()));
        } catch (Exception e) {
            log.warn("[TCP] Failed to parse JSON: {}", json, e);
        }
    }

    /**
     * Sends a command string to the TCP server.
     *
     * @param c the command to send
     */
    private void sendCmd(String c) {
        if (out != null) {
            out.println(c);
            out.flush();
            log.trace("[TCP] Sent command: {}", c);
        } else {
            log.warn("[TCP] Output stream is null. Command not sent: {}", c);
        }
    }

    /**
     * Sleeps the current thread for the specified duration.
     *
     * @param millis milliseconds to sleep
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
