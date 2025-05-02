package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.model.AssetState;

import java.time.Instant;

/**
 * Service interface for applying shock logic to asset states.
 * Handles both automatic and critical shocks.
 *
 * @author Ömer Asaf BALIKÇI
 */
public interface ShockService {
    /**
     * Applies automatic shocks based on internal configuration.
     *
     * @param state asset state to modify
     * @param now   current simulation timestamp
     */
    void processAutomaticShocks(AssetState state, Instant now);

    /**
     * Applies event-based or critical shocks if conditions are met.
     *
     * @param state asset state to evaluate
     * @param now   current simulation timestamp
     */
    void checkAndApplyCriticalShocks(AssetState state, Instant now);
}
