package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.subscriber.AbstractSubscriber;
import com.ratedistribution.ratehub.subscriber.RateListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TcpSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(TcpSubscriber.class);
    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private PrintWriter out;

    public TcpSubscriber(RateListener l, String name, String host, int port) {
        super(l, name);
        this.host = host;
        this.port = port;
    }

    @Override
    public void subscribe(String rateName) {
        super.subscribe(rateName);
        if (out != null) {
            out.println("subscribe|" + rateName);
        }
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            this.out = writer;
            subs.forEach(r -> out.println("subscribe|" + r));

            String line;
            while (running && (line = in.readLine()) != null) {
                processLine(line);
            }

        } catch (Exception e) {
            listener.onRateError(platform, "socket", e);
        } finally {
            disconnect();
        }
    }

    private void processLine(String line) {
        if (!line.startsWith("{")) return;

        try {
            Rate rate = mapper.readValue(line, Rate.class);
            if (!subs.contains(rate.rateName())) return;

            received.incrementAndGet();
            listener.onRateAvailable(platform, rate.rateName(), rate);
            listener.onRateUpdate(platform, rate.rateName(),
                    new RateFields(rate.bid(), rate.ask(), rate.timestamp()));
        } catch (Exception e) {
            log.warn("Failed to parse rate JSON: {}", line);
            listener.onRateError(platform, "parsing", e);
        }
    }
}
