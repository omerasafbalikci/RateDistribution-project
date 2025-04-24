package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    public Map<String, CalcDef> toCalcDefs() {
        return calculations.stream().collect(Collectors.toMap(c -> c.rateName, c -> new CalcDef(c.rateName(), c.engine(), c.bid(), c.ask(), Optional.ofNullable(c.helpers()).orElseGet(HashMap::new), extractRefs(c))));
    }

    private static Set<String> extractRefs(CalcCfg c) {
        Pattern p = Pattern.compile("[A-Z0-9_]{5,}");
        Set<String> rs = new HashSet<>();
        rs.addAll(p.matcher(c.bid()).results().map(MatchResult::group).toList());
        rs.addAll(p.matcher(c.ask()).results().map(MatchResult::group).toList());
        Optional.ofNullable(c.helpers()).ifPresent(h -> h.values().forEach(f -> rs.addAll(p.matcher(f).results().map(MatchResult::group).toList())));
        return rs;
    }

}
