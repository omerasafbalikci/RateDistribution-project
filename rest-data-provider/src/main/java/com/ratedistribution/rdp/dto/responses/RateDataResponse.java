package com.ratedistribution.rdp.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ratedistribution.rdp.utilities.serializer.PercentSerializer;
import com.ratedistribution.rdp.utilities.serializer.PriceSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for returning rate data over REST.
 * Includes pricing, volume, and change metrics.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateDataResponse implements Serializable {
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
