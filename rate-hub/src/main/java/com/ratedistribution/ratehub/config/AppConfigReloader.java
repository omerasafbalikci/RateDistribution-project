package com.ratedistribution.ratehub.config;

import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
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
        Thread.startVirtualThread(() -> {
            try {
                FileTime lastModified = Files.getLastModifiedTime(configPath);
                log.info("[AppConfigReloader] Starting polling for config changes: {}", configPath);

                while (true) {
                    Thread.sleep(3000); // Check every 3 seconds
                    FileTime currentModified = Files.getLastModifiedTime(configPath);
                    if (!currentModified.equals(lastModified)) {
                        lastModified = currentModified;
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
            } catch (Exception e) {
                GlobalExceptionHandler.handle("AppConfigReloader.pollingWatcher", e);
            }
        });
    }
}
