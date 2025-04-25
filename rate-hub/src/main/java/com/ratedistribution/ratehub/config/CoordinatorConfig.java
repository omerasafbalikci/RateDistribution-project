package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ratedistribution.ratehub.service.Coordinator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record CoordinatorConfig(SystemCfg hazelcast, KafkaCfg kafka, ThreadCfg threadPool,
                                List<SubscriberCfg> subscribers, List<CalcCfg> calculations) {
    public static CoordinatorConfig load(Path p) throws Exception {
        ObjectMapper m = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        return m.readValue(p.toFile(), CoordinatorConfig.class);
    }

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

    public Map<String, Coordinator.CalcDef> toCalcDefs() {
        return calculations.stream().collect(Collectors.toMap(CalcCfg::rateName, this::toDef));
    }

    private Coordinator.CalcDef toDef(CalcCfg c) {
        return new Coordinator.CalcDef(c.rateName, c.engine, c.bid, c.ask,
                Optional.ofNullable(c.helpers).map(h -> h.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new BigDecimal(e.getValue())))).orElseGet(HashMap::new),
                extractRefs(c));
    }

    private static Set<String> extractRefs(CalcCfg c) {
        Pattern p = Pattern.compile("[A-Z0-9_]{5,}");
        Set<String> rs = new HashSet<>();
        rs.addAll(p.matcher(c.bid).results().map(MatchResult::group).toList());
        rs.addAll(p.matcher(c.ask).results().map(MatchResult::group).toList());
        return rs;
    }
}
