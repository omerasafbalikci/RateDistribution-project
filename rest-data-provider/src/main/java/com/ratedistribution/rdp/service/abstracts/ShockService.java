package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.model.AssetState;

import java.time.LocalDateTime;

public interface ShockService {
    void processAutomaticShocks(AssetState state, LocalDateTime now);

    void checkAndApplyCriticalShocks(AssetState state, LocalDateTime now);
}
