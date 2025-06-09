package com.ratedistribution.ratehub.config;

public interface ConfigChangeListener {
    void onConfigChange(CoordinatorConfig newConfig);
}
