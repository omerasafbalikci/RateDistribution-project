package com.ratedistribution.tdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ratedistribution.tdp.advice.GlobalExceptionHandler;
import com.ratedistribution.tdp.utilities.exceptions.ConfigurationLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads YAML configuration and watches for file changes.
 * On reload, notifies registered listeners.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class SimulatorConfigLoader {
    private static final Logger log = LogManager.getLogger(SimulatorConfigLoader.class);
    private static final Path YAML_PATH = Paths.get("tcp-data-provider/config/application.yml");
    private final ObjectMapper mapper;
    private final AtomicReference<ApplicationConfig> appCfg = new AtomicReference<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public SimulatorConfigLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

        try {
            load();
        } catch (IOException e) {
            throw new ConfigurationLoadException("Initial config load failed", e);
        }

        watchChanges();
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public JwtConfig jwt() {
        return appCfg.get().getJwt();
    }

    public RedisConfig redis() {
        return appCfg.get().getRedis();
    }

    public SimulatorProperties currentSimulator() {
        return appCfg.get().getSimulator();
    }

    public int currentPort() {
        return appCfg.get().getTcp().getPort();
    }

    private void load() throws IOException {
        try (InputStream in = Files.newInputStream(YAML_PATH)) {
            appCfg.set(mapper.readValue(in, ApplicationConfig.class));
            log.info("Configuration reloaded from '{}'", YAML_PATH);
            listeners.forEach(Runnable::run);
        }
    }

    private void watchChanges() {
        Thread.ofVirtual().start(() -> {
            try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                YAML_PATH.getParent().register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                log.info("Watching '{}' for changes...", YAML_PATH);

                while (true) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals(YAML_PATH.getFileName().toString())) {
                            try {
                                load();
                            } catch (IOException e) {
                                GlobalExceptionHandler.handle("Config Reload", e);
                            }
                        }
                    }
                    if (!key.reset()) {
                        log.warn("WatchKey no longer valid. Stopping watch.");
                        break;
                    }
                }
            } catch (Exception e) {
                GlobalExceptionHandler.handle("WatchService", e);
            }
        });
    }
}
