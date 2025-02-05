package com.ratedistribution.rdp.dto;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.AssetState;

public record RateUpdateResult(AssetState newState, RateDataResponse response) {
}
