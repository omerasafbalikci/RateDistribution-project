package com.ratedistribution.ratehub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.Path;

public class AppConfigLoader {
    public static CoordinatorConfig load(Path p) throws Exception {
        ObjectMapper m = new ObjectMapper(new YAMLFactory());
        m.findAndRegisterModules();
        return m.readValue(p.toFile(), CoordinatorConfig.class);
    }
}
