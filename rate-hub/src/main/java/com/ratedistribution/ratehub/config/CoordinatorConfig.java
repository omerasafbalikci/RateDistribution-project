package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ratedistribution.ratehub.advice.GlobalExceptionHandler;
import com.ratedistribution.ratehub.coord.CalcDef;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Root configuration record for the Rate Hub application.
 * Represents system, Kafka, threading, calculation, authentication, mail, and subscriber configurations.
 *
 * @author Ömer Asaf BALIKÇI
 */

public record CoordinatorConfig(SystemCfg hazelcast, KafkaCfg kafka, ThreadCfg threadPool,
                                List<SubscriberCfg> subscribers, List<CalcCfg> calculations,
                                MailCfg mail, AuthCfg auth) {
    /**
     * Hazelcast system configuration.
     *
     * @param clusterName name of the Hazelcast cluster
     */
    public record SystemCfg(String clusterName) {
    }

    /**
     * Kafka configuration for topics and bootstrap servers.
     */
    public record KafkaCfg(String bootstrapServers, String rawTopic, String calcTopic) {
    }

    /**
     * Thread pool configuration.
     *
     * @param size the size of the thread pool
     */
    public record ThreadCfg(int size) {
    }

    /**
     * Subscriber configuration mapping incoming data.
     */
    public record SubscriberCfg(@JsonProperty("class") String className, String name, String host, int port,
                                List<String> rates) {
    }

    /**
     * Configuration for calculated rates, includes script and helpers.
     */
    public record CalcCfg(
            String rateName,
            String engine,
            String scriptPath,
            Map<String, String> helpers,
            List<String> dependsOn
    ) {
    }

    /**
     * Authentication configuration for token retrieval.
     */
    public record AuthCfg(String url, String username, String password, int refreshSkewSeconds) {
    }

    /**
     * Email configuration for outgoing alert messages.
     */
    public record MailCfg(
            String from,
            String password,
            String to,
            String smtpHost,
            int smtpPort
    ) {
    }

    /**
     * Loads CoordinatorConfig from the specified YAML file.
     * Also resolves relative script paths to absolute.
     *
     * @param yaml path to YAML configuration file
     * @return loaded and enriched CoordinatorConfig
     */
    public static CoordinatorConfig load(Path yaml) {
        try {
            ObjectMapper om = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
            CoordinatorConfig original = om.readValue(yaml.toFile(), CoordinatorConfig.class);
            Path base = yaml.getParent();

            List<CalcCfg> updatedCalcs = original.calculations().stream()
                    .map(c -> new CalcCfg(
                            c.rateName(),
                            c.engine(),
                            base.resolve(c.scriptPath()).toAbsolutePath().toString(),
                            c.helpers(),
                            c.dependsOn() != null ? c.dependsOn() : extractRefs(base.resolve(c.scriptPath()))
                    ))
                    .toList();

            return new CoordinatorConfig(
                    original.hazelcast(),
                    original.kafka(),
                    original.threadPool(),
                    original.subscribers(),
                    updatedCalcs,
                    original.mail(),
                    original.auth()
            );

        } catch (Exception e) {
            GlobalExceptionHandler.fatal("CoordinatorConfig.load", e);
            return null;
        }
    }

    /**
     * Converts all calculation configurations into a map of CalcDef instances.
     *
     * @return map of rateName to CalcDef
     */
    public Map<String, CalcDef> toDefs() {
        return calculations.stream()
                .collect(Collectors.toMap(CalcCfg::rateName, this::toDef));
    }

    private CalcDef toDef(CalcCfg c) {
        Map<String, BigDecimal> parsedHelpers = c.helpers() != null
                ? c.helpers().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new BigDecimal(e.getValue())))
                : Map.of();

        Set<String> deps = new HashSet<>(c.dependsOn() != null ? c.dependsOn() : List.of());

        return new CalcDef(
                c.rateName(),
                c.engine(),
                c.scriptPath(),
                parsedHelpers,
                deps
        );
    }

    private static List<String> extractRefs(Path scriptPath) {
        try {
            if (!Files.exists(scriptPath)) return List.of();
            String content = Files.readString(scriptPath);
            Pattern p = Pattern.compile("[A-Z]{3,6}[A-Z0-9_/]*");
            return p.matcher(content).results()
                    .map(MatchResult::group)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}