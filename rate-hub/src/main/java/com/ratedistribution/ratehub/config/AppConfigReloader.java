package com.ratedistribution.ratehub.config;

import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private volatile CoordinatorConfig currentConfig;
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();

    public AppConfigReloader(Path configPath, CoordinatorConfig initialConfig) {
        this.configPath = configPath;
        this.currentConfig = initialConfig;
    }

    public CoordinatorConfig getConfig() {
        return currentConfig;
    }

    public void addListener(ConfigChangeListener l) {
        listeners.add(l);
    }

    public void removeListener(ConfigChangeListener l) {
        listeners.remove(l);
    }

    public void startWatching() {
        Thread.startVirtualThread(() -> {
            try {
                FileTime lastModified = Files.getLastModifiedTime(configPath);
                log.info("[AppConfigReloader] Starting polling for config changes: {}", configPath);

                while (true) {
                    Thread.sleep(3000);
                    FileTime currentModified = Files.getLastModifiedTime(configPath);
                    if (!currentModified.equals(lastModified)) {
                        lastModified = currentModified;
                        log.warn("[AppConfigReloader] Configuration file changed. Reloading...");
                        CoordinatorConfig updated = AppConfigLoader.load(configPath);
                        if (updated != null) {
                            currentConfig = updated;
                            listeners.forEach(l -> l.onConfigChange(updated));
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
