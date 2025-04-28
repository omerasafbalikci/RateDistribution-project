package com.ratedistribution.ratehub.coord;

import com.ratedistribution.ratehub.subscriber.Subscriber;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class SubSupervisor implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(SubSupervisor.class);
    private final List<Subscriber> subs;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    public void start() {
        exec.scheduleAtFixedRate(this::probe, 5, 5, TimeUnit.SECONDS);
    }

    private void probe() {
        subs.forEach(s -> {
            log.debug(s.status());
            if (!s.isConnected()) try {
                s.connect("", "");
            } catch (Exception e) {
                log.error(e);
            }
        });
    }

    @Override
    public void close() {
        exec.shutdownNow();
    }
}