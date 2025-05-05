package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ratedistribution.ratehub.coord.CalcDef;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record CoordinatorConfig(SystemCfg hazelcast, KafkaCfg kafka, ThreadCfg threadPool,
                                List<SubscriberCfg> subscribers, List<CalcCfg> calculations,
                                MailCfg mail, AuthCfg auth) {

    public record SystemCfg(String clusterName) {
    }

    public record KafkaCfg(String bootstrapServers, String rawTopic, String calcTopic) {
    }

    public record ThreadCfg(int size) {
    }

    public record SubscriberCfg(@JsonProperty("class") String className, String name, String host, int port,
                                List<String> rates) {
    }

    public record CalcCfg(
            String rateName,
            String engine,
            String scriptPath,
            Map<String, String> helpers
    ) {
    }

    public record AuthCfg(String url, String username, String password, int refreshSkewSeconds) {
    }

    public static CoordinatorConfig load(Path yaml) {
        try {
            ObjectMapper om = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
            CoordinatorConfig original = om.readValue(yaml.toFile(), CoordinatorConfig.class);

            Path base = yaml.getParent();

            // Yeni scriptPath’ler ile yeni CalcCfg listesi oluştur
            List<CalcCfg> updatedCalcs = original.calculations().stream()
                    .map(c -> new CalcCfg(
                            c.rateName(),
                            c.engine(),
                            base.resolve(c.scriptPath()).toAbsolutePath().toString(),
                            c.helpers()
                    ))
                    .toList();

            // Yeni CoordinatorConfig oluştur (diğer tüm alanları koruyarak)
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
            throw new RuntimeException("Unable to read config: " + yaml.toAbsolutePath(), e);
        }
    }

    public record MailCfg(
            String from,
            String password,
            String to,
            String smtpHost,
            int smtpPort
    ) {
    }

    public Map<String, CalcDef> toDefs() {
        return calculations.stream()
                .collect(Collectors.toMap(CalcCfg::rateName, this::toDef));
    }

    private CalcDef toDef(CalcCfg c) {

        /* helpers → BigDecimal */
        Map<String,BigDecimal> helpers = Optional.ofNullable(c.helpers)
                .map(m -> m.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> new BigDecimal(e.getValue()))))
                .orElseGet(Map::of);

        /*  Bağımlı sembolleri sadece regex ile çıkar (script dosyasını
            yüklemek DynamicScriptFormulaEngine’e bırakılır) */
        Set<String> deps = refs(c.scriptPath);

        return new CalcDef(
                c.rateName,
                c.engine,
                c.scriptPath,
                helpers,
                deps);
    }

    private static Set<String> refs(String scriptPath) {
        // Sadece path içindeki büyük harfli dizeleri alma hilesi; gerçek bağımlılık
        //   DynamicScriptFormulaEngine içinde daima güncel okunur.
        Pattern p = Pattern.compile("[A-Z]{3,6}[A-Z0-9_/]*");
        return p.matcher(scriptPath).results()
                .map(MatchResult::group)
                .collect(Collectors.toSet());
    }
}