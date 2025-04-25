package com.ratedistribution.consumerdb.listener;

import com.ratedistribution.consumerdb.model.RateEntity;
import com.ratedistribution.consumerdb.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Log4j2
@Component
@RequiredArgsConstructor
public class RateListener {
    private final RateRepository repo;

    @KafkaListener(topics = "${ratehub.topic}", containerFactory = "factory")
    public void consume(String message) {
        try {
            String[] p = message.split("\\|");
            RateEntity e = RateEntity.builder()
                    .rateName(p[0])
                    .bid(new BigDecimal(p[1]))
                    .ask(new BigDecimal(p[2]))
                    .rateUpdatetime(LocalDateTime.parse(p[3]))
                    .dbUpdatetime(LocalDateTime.now())
                    .build();
            repo.save(e);
        } catch (Exception ex) {
            log.error("Failed to persist {}", message, ex);
        }
    }
}
