package com.ratedistribution.consumerlog.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class LogListener {
    private final OpenSearchClient osc;

    @KafkaListener(topics = "${ratehub.topic}", containerFactory = "factory")
    public void listen(String line) {
        try {
            String[] p = line.split("\\|");
            Map<String, Object> doc = Map.of(
                    "rateName", p[0],
                    "bid", new BigDecimal(p[1]),
                    "ask", new BigDecimal(p[2]),
                    "timestamp", LocalDateTime.parse(p[3]));
            osc.index(i -> i.index("ratehub-log").document(doc));
        } catch (Exception e) {
            log.error("OS index fail {}", line, e);
        }
    }
}
