package com.ratedistribution.ratehub.config;

import java.nio.file.Path;

/**
 * Utility class for loading application-level configuration.
 * Delegates the loading process to the CoordinatorConfig class.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class AppConfigLoader {
    public static CoordinatorConfig load(Path p) {
        return CoordinatorConfig.load(p);
    }
}
