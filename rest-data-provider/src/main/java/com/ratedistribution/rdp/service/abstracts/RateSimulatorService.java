package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;

import java.util.List;

public interface RateSimulatorService {
    List<RateDataResponse> updateAllRates();
}
