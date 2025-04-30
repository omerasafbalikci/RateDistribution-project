package com.ratedistribution.tdp.dto.responses;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ratedistribution.tdp.utilities.serializer.PercentSerializer;
import com.ratedistribution.tdp.utilities.serializer.PriceSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateDataResponse {
    private String rateName;
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal bid;
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal ask;
    private Instant timestamp;
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayOpen;
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayHigh;
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayLow;
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayChange;
    @JsonSerialize(using = PercentSerializer.class)
    private BigDecimal dayChangePercent;
    private Long dayVolume;
    private Long lastTickVolume;
}
