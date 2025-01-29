package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.model.RateDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "simulator")
@RefreshScope
public class SimulatorProperties {
    private long updateIntervalMillis;
    private int maxUpdates;
    private double dynamicSpreadAlpha; // spread'i dinamikleştirmek için
    private List<RateDefinition> rates;

    public long getUpdateIntervalMillis() {
        return updateIntervalMillis;
    }

    public void setUpdateIntervalMillis(long updateIntervalMillis) {
        this.updateIntervalMillis = updateIntervalMillis;
    }

    public int getMaxUpdates() {
        return maxUpdates;
    }

    public void setMaxUpdates(int maxUpdates) {
        this.maxUpdates = maxUpdates;
    }

    public double getDynamicSpreadAlpha() {
        return dynamicSpreadAlpha;
    }

    public void setDynamicSpreadAlpha(double dynamicSpreadAlpha) {
        this.dynamicSpreadAlpha = dynamicSpreadAlpha;
    }

    public List<RateDefinition> getRates() {
        return rates;
    }

    public void setRates(List<RateDefinition> rates) {
        this.rates = rates;
    }
}
