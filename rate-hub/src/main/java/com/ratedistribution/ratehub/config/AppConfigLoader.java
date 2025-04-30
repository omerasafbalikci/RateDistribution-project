package com.ratedistribution.ratehub.config;

import java.nio.file.Path;

public class AppConfigLoader {
    public static CoordinatorConfig load(Path p) throws Exception {
        return CoordinatorConfig.load(p);
    }
}
