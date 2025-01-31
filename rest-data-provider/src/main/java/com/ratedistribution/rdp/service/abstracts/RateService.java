package com.ratedistribution.rdp.service.abstracts;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;

public interface RateService {
    void updateRates();

    RateDataResponse getRate(String rateName);
}
