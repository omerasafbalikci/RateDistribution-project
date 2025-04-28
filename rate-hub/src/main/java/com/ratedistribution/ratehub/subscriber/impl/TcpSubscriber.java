package com.ratedistribution.ratehub.subscriber.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.model.RateFields;
import com.ratedistribution.ratehub.model.RawTick;
import com.ratedistribution.ratehub.subscriber.AbstractSubscriber;
import com.ratedistribution.ratehub.coord.RateListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Pattern;

public class TcpSubscriber extends AbstractSubscriber {
    private static final Logger log = LogManager.getLogger(TcpSubscriber.class);
    private static final Pattern CMD = Pattern.compile("^[A-Z]+\\|.*");
    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private PrintWriter out;

    public TcpSubscriber(RateListener l, String name, String host, int port) {
        super(l, name);
        this.host = host;
        this.port = port;
    }

    @Override
    public void subscribe(String rateName) {
        super.subscribe(rateName);
        if (out != null) out.println("subscribe|" + rateName);
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out = new PrintWriter(socket.getOutputStream(), true);
            subs.forEach(r -> out.println("subscribe|" + r));
            String line;
            while (running && (line = in.readLine()) != null) {
                if (CMD.matcher(line).matches()) continue;
                if (!line.startsWith("{")) continue;
                process(line);
            }
        } catch (Exception e) {
            listener.onRateError(platform, "SOCKET", e);
        } finally {
            disconnect();
        }
    }

    private void process(String json) {
        try {
            RawTick rawTick = mapper.readValue(json, RawTick.class);
            if (!subs.contains(rawTick.rateName())) return;
            received.incrementAndGet();
            listener.onRateAvailable(platform, rawTick.rateName(), rawTick);
            listener.onRateUpdate(platform, rawTick.rateName(), new RateFields(rawTick.bid(), rawTick.ask(), rawTick.timestamp()));
        } catch (Exception ex) {
            log.warn("BAD JSON {}", json, ex);
        }
    }
}
