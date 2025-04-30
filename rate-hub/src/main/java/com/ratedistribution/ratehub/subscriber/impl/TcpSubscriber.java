package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class TcpSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(TcpSubscriber.class);
    private static final Pattern CMD = Pattern.compile("^[A-Z]+\\|.*");
    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private PrintWriter out;
    private final List<Long> backoffSeq = List.of(1000L, 2000L, 4000L, 8000L, 16000L, 32000L);

    public TcpSubscriber(RateListener listener, String platformName, String host, int port) {
        super(listener, platformName);
        this.host = host;
        this.port = port;
    }

    @Override
    public void subscribe(String rateName) {
        super.subscribe(rateName);
        sendCmd("subscribe|" + rateName);
    }

    @Override
    public void unsubscribe(String rateName) {
        super.unsubscribe(rateName);
        sendCmd("unsubscribe|" + rateName);
    }

    @Override
    public void run() {
        int attempt = 0;
        while (running) {
            try (Socket socket = new Socket(host, port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                this.out = output;
                resubscribeAll();
                attempt = 0;

                String line;
                while (running && (line = in.readLine()) != null) {
                    if (CMD.matcher(line).matches() || !line.startsWith("{")) continue;
                    process(line);
                }
            } catch (Exception e) {
                listener.onRateError(platform, "TCP", e);
                long delay = backoffSeq.get(Math.min(attempt++, backoffSeq.size() - 1));
                sleep(delay);
            }
        }
    }

    private void resubscribeAll() {
        subs.forEach(r -> sendCmd("subscribe|" + r));
    }

    private void process(String json) {
        try {
            RawTick raw = mapper.readValue(json, RawTick.class);
            if (!subs.contains(raw.rateName())) return;
            markReceived();
            listener.onRateAvailable(platform, raw.rateName(), raw);
            listener.onRateUpdate(platform, raw.rateName(), new RateFields(raw.bid(), raw.ask(), raw.timestamp()));
        } catch (Exception e) {
            log.warn("[TCP] Bad JSON: {}", json, e);
        }
    }

    private void sendCmd(String c) {
        if (out != null) {
            out.println(c);
            out.flush();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
