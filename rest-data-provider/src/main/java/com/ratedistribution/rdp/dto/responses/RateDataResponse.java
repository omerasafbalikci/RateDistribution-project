package com.ratedistribution.rdp.dto.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ratedistribution.rdp.utilities.serializer.PercentSerializer;
import com.ratedistribution.rdp.utilities.serializer.PriceSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayOpen;

    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayHigh;

    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayLow;

    // Gün içi mutlak fark (mid - dayOpen)
    @JsonSerialize(using = PriceSerializer.class)
    private BigDecimal dayChange;

    // Gün içi yüzde değişim
    @JsonSerialize(using = PercentSerializer.class)
    private BigDecimal dayChangePercent;

    // Günlük kümülatif hacim
    private Long dayVolume;

    // Opsiyonel: Son tick hacmi
    private Long lastTickVolume;
}
