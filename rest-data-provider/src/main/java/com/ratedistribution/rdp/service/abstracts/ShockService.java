package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.model.AssetState;

import java.time.Instant;
import java.time.LocalDateTime;

public interface ShockService {
    void processAutomaticShocks(AssetState state, Instant now);

    void checkAndApplyCriticalShocks(AssetState state, Instant now);
}
