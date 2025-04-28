package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ratedistribution.ratehub.coord.CalcDef;
import com.ratedistribution.ratehub.coord.Coordinator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record CoordinatorConfig(SystemCfg hazelcast, KafkaCfg kafka, ThreadCfg threadPool,
                                List<SubscriberCfg> subscribers, List<CalcCfg> calculations) {
    public record SystemCfg(String clusterName) {
    }

    public record KafkaCfg(String bootstrapServers, String topic) {
    }

    public record ThreadCfg(int size) {
    }

    public record SubscriberCfg(@JsonProperty("class") String className, String name, String host, int port,
                                List<String> rates) {
    }

    public record CalcCfg(String rateName, String engine, String bid, String ask, Map<String, String> helpers) {
    }

    public static CoordinatorConfig load(Path p) throws Exception {
        return new ObjectMapper(new YAMLFactory()).findAndRegisterModules().readValue(p.toFile(), CoordinatorConfig.class);
    }

    public Map<String, CalcDef> toDefs() {
        return calculations.stream().collect(Collectors.toMap(CalcCfg::rateName, this::toDef));
    }

    private CalcDef toDef(CalcCfg c) {
        Map<String, BigDecimal> hl = Optional.ofNullable(c.helpers).map(m -> m.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new BigDecimal(e.getValue())))).orElse(Map.of());
        Set<String> refs = refs(c.bid);
        refs.addAll(refs(c.ask));
        return new CalcDef(c.rateName, c.engine, c.bid, c.ask, hl, refs);
    }

    private static Set<String> refs(String expr) {
        Pattern p = Pattern.compile("[A-Z]{3,6}[A-Z0-9_/]*");
        return p.matcher(expr).results().map(MatchResult::group).collect(Collectors.toSet());
    }

}
