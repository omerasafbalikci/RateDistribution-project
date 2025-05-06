package com.ratedistribution.ratehub.config;

import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Watches the YAML configuration file and reloads {@link CoordinatorConfig}
 * at runtime when the file is modified.
 * Provides live config updates for dynamic system behavior.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class AppConfigReloader {
    private static final Logger log = LogManager.getLogger(AppConfigReloader.class);
    private final Path configPath;
    private final AtomicReference<CoordinatorConfig> currentConfig;
    private WatchService watchService;

    public AppConfigReloader(Path configPath, CoordinatorConfig initialConfig) {
        this.configPath = configPath;
        this.currentConfig = new AtomicReference<>(initialConfig);
    }

    public CoordinatorConfig getConfig() {
        return currentConfig.get();
    }

    public void startWatching() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path dir = configPath.getParent();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            log.info("[AppConfigReloader] Watching config directory: {}", dir);

            Thread.startVirtualThread(() -> {
                while (true) {
                    try {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changed = (Path) event.context();
                            if (changed.endsWith(configPath.getFileName())) {
                                log.warn("[AppConfigReloader] Configuration file changed. Reloading...");
                                CoordinatorConfig updated = AppConfigLoader.load(configPath);
                                if (updated != null) {
                                    currentConfig.set(updated);
                                    log.info("[AppConfigReloader] Configuration reloaded successfully.");
                                } else {
                                    log.warn("[AppConfigReloader] Failed to reload configuration.");
                                }
                            }
                        }
                        key.reset();
                    } catch (InterruptedException e) {
                        log.info("[AppConfigReloader] Watcher interrupted. Stopping...");
                        return;
                    } catch (Exception e) {
                        GlobalExceptionHandler.handle("AppConfigReloader.startWatching", e);
                    }
                }
            });
        } catch (IOException e) {
            GlobalExceptionHandler.fatal("AppConfigReloader.init", e);
        }
    }
}
