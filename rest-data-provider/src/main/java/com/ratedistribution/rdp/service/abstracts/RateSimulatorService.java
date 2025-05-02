package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;

import java.util.List;

/**
 * Service interface for updating simulated rate data.
 *
 * @author Ömer Asaf BALIKÇI
 */

public interface RateSimulatorService {
    /**
     * Triggers update for all rates in the system.
     *
     * @return list of updated rate responses
     */
    List<RateDataResponse> updateAllRates();
}
