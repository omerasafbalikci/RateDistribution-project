package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ratedistribution.ratehub.coord.CalcDef;
import com.ratedistribution.ratehub.utilities.FormulaValidator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record CoordinatorConfig(SystemCfg hazelcast, KafkaCfg kafka, ThreadCfg threadPool,
                                List<SubscriberCfg> subscribers, List<CalcCfg> calculations, MailCfg mail) {

    public record SystemCfg(String clusterName) {
    }

    public record KafkaCfg(String bootstrapServers, String rawTopic, String calcTopic) {
    }

    public record ThreadCfg(int size) {
    }

    public record SubscriberCfg(@JsonProperty("class") String className, String name, String host, int port,
                                List<String> rates) {
    }

    public record CalcCfg(String rateName, String engine, String bid, String ask, Map<String, String> helpers) {
    }

    public static CoordinatorConfig load(Path path) throws Exception {
        try {
            CoordinatorConfig config = new ObjectMapper(new YAMLFactory())
                    .findAndRegisterModules()
                    .readValue(path.toFile(), CoordinatorConfig.class);

            // Formül doğrulama (erken hata tespiti)
            Map<String, CalcDef> defs = config.toDefs();
            for (CalcDef def : defs.values()) {
                Set<String> bidVars = FormulaValidator.extractVariables(def.bidFormula());
                Set<String> askVars = FormulaValidator.extractVariables(def.askFormula());
                Set<String> allVars = new HashSet<>(bidVars);
                allVars.addAll(askVars);

                for (String v : allVars) {
                    String base = v.split("_")[0];
                    if (!def.dependsOn().contains(base)) {
                        throw new IllegalArgumentException("Formula for " + def.rateName() + " references unknown symbol: " + base);
                    }
                }

                // Helpers kontrolü
                Set<String> helperKeys = def.helpers() != null ? def.helpers().keySet() : Set.of();
                if (!FormulaValidator.validateHelpers(helperKeys, def.helpers())) {
                    Set<String> missing = FormulaValidator.getMissingHelpers(helperKeys, def.helpers());
                    if (!missing.isEmpty()) {
                        throw new IllegalArgumentException("Missing helper values for " + def.rateName() + ": " + missing);
                    }
                }
            }

            return config;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load coordinator configuration from " + path.toAbsolutePath(), e);
        }
    }

    public Map<String, CalcDef> toDefs() {
        return calculations.stream().collect(Collectors.toMap(CalcCfg::rateName, this::toDef));
    }

    public record MailCfg(
            String from,
            String password,
            String to,
            String smtpHost,
            int smtpPort
    ) {
    }

    private CalcDef toDef(CalcCfg c) {
        Map<String, BigDecimal> hl = Optional.ofNullable(c.helpers)
                .map(m -> m.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new BigDecimal(e.getValue()))))
                .orElse(Map.of());

        Set<String> refs = refs(c.bid);
        refs.addAll(refs(c.ask));

        return new CalcDef(c.rateName, c.engine, c.bid, c.ask, hl, refs);
    }

    private static Set<String> refs(String expr) {
        Pattern p = Pattern.compile("[A-Z]{3,6}[A-Z0-9_/]*");
        return p.matcher(expr).results().map(MatchResult::group).collect(Collectors.toSet());
    }
}