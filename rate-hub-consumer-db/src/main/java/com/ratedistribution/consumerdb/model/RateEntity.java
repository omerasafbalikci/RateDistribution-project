package com.ratedistribution.consumerdb.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity class representing a rate record stored in the database.
 * Maps to the table {@code TblRates} and holds bid/ask data along with timestamps.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Entity
@Table(name = "TblRates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String rateName;
    private BigDecimal bid;
    private BigDecimal ask;
    private LocalDateTime rateUpdatetime;
    private LocalDateTime dbUpdatetime;
    @Column(length = 20)
    private String sourceType;
}
