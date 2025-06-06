package com.ratedistribution.consumerdb.listener;

import com.ratedistribution.consumerdb.model.RateEntity;
import com.ratedistribution.consumerdb.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RateListener listens to Kafka topics for raw and calculated rate messages.
 * It parses and stores incoming rate data into the database via {@link RateRepository}.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Log4j2
@Component
@RequiredArgsConstructor
public class RateListener {
    private final RateRepository repository;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    @KafkaListener(topics = "${ratehub.raw-topic}", containerFactory = "factory")
    public void consumeRaw(String message) {
        log.trace("[RAW] Received message: {}", message);
        processMessage(message, "RAW");
    }

    @KafkaListener(topics = "${ratehub.calc-topic}", containerFactory = "factory")
    public void consumeCalculated(String message) {
        log.trace("[CALCULATED] Received message: {}", message);
        processMessage(message, "CALCULATED");
    }

    private void processMessage(String message, String sourceType) {
        try {
            String[] p = message.split("\\|");
            if (p.length != 4) {
                log.warn("[{}] Skipped malformed message: {}", sourceType, message);
                return;
            }

            RateEntity e = RateEntity.builder()
                    .rateName(p[0])
                    .bid(new BigDecimal(p[1]))
                    .ask(new BigDecimal(p[2]))
                    .rateUpdatetime(LocalDateTime.parse(p[3], formatter))
                    .dbUpdatetime(LocalDateTime.now())
                    .sourceType(sourceType)
                    .build();

            repository.save(e);
            log.debug("[{}] Saved rate: {}", sourceType, e.getRateName());
        } catch (Exception ex) {
            log.error("[{}] Failed to process message: {} - {}", sourceType, message, ex.getMessage(), ex);
        }
    }
}
