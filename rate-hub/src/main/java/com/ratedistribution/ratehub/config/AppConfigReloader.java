package com.ratedistribution.ratehub.config;

import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Watches the YAML configuration file and reloads {@link CoordinatorConfig}
 * at runtime when the file is modified.
 * Provides live config updates for dynamic system behavior.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class AppConfigReloader {
    private static final Logger log = LogManager.getLogger(AppConfigReloader.class);
    private final Path configFile;
    private final Path scriptsDir;
    private volatile CoordinatorConfig currentConfig;
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private WatchService watchService;

    public AppConfigReloader(Path configFile, CoordinatorConfig initialConfig) {
        this.configFile = configFile;
        this.currentConfig = initialConfig;
        this.scriptsDir = configFile.getParent().resolve("formulas");
    }

    public CoordinatorConfig getCurrentConfig() {
        return currentConfig;
    }

    public void registerListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public void start() {
        Thread thread = new Thread(this::watchLoop, "Config-Reloader");
        thread.setDaemon(true);
        thread.start();
        log.info("Started watching {} and {}", configFile, scriptsDir);
    }

    private void watchLoop() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerPath(configFile.getParent());
            if (Files.isDirectory(scriptsDir)) {
                registerRecursively(scriptsDir);
            }

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = ((Path) key.watchable()).resolve((Path) event.context());
                    if (isConfigFile(changed) || isScriptFile(changed)) {
                        log.info("Change detected on {}: {}", changed, event.kind());
                        reloadConfig();
                    }
                    if (event.kind() == ENTRY_CREATE && Files.isDirectory(changed)) {
                        registerRecursively(changed);
                    }
                }
                if (!key.reset()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Config watcher interrupted");
        } catch (IOException e) {
            GlobalExceptionHandler.handle("AppConfigReloader.watchLoop", e);
        }
    }

    private void reloadConfig() {
        try {
            CoordinatorConfig updated = AppConfigLoader.load(configFile);
            if (updated != null) {
                currentConfig = updated;
                listeners.forEach(l -> l.onConfigChange(updated));
                log.info("Configuration reloaded");
            } else {
                log.warn("Reload returned null config");
            }
        } catch (Exception e) {
            GlobalExceptionHandler.handle("AppConfigReloader.reloadConfig", e);
        }
    }

    private void registerPath(Path dir) throws IOException {
        dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
    }

    private void registerRecursively(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerPath(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isConfigFile(Path path) {
        return path.equals(configFile) && path.getFileName().toString().endsWith(".yml");
    }

    private boolean isScriptFile(Path path) {
        String fn = path.getFileName().toString().toLowerCase();
        return fn.endsWith(".groovy") || fn.endsWith(".java") || fn.endsWith(".js");
    }
}
