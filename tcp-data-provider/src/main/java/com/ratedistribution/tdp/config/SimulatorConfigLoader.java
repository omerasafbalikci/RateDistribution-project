package com.ratedistribution.tdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SimulatorConfigLoader {
    private static final Path YAML_PATH = Paths.get("tcp-data-provider/config/application.yml");
    private final ObjectMapper mapper;
    private final AtomicReference<ApplicationConfig> appCfg = new AtomicReference<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public SimulatorConfigLoader() throws IOException {
        mapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule());
        load();
        watchChanges();
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public SimulatorProperties currentSimulator() {
        return appCfg.get().getSimulator();
    }

    public int currentPort() {
        return appCfg.get().getPort();
    }

    private void load() throws IOException {
        try (InputStream in = Files.newInputStream(YAML_PATH)) {
            appCfg.set(mapper.readValue(in, ApplicationConfig.class));
            listeners.forEach(Runnable::run);
        }
    }

    private void watchChanges() {
        Thread t = Thread.ofVirtual().start(() -> {
            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                YAML_PATH.getParent().register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals(YAML_PATH.getFileName().toString())) {
                            load();
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}