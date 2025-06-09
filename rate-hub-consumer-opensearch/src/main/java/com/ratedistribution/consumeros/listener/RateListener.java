package com.ratedistribution.consumeros.listener;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RateListener {
    private final OpenSearchClient client;
    @Value("${opensearch.index-name}")
    private String indexName;

    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    @KafkaListener(topics = "${ratehub.raw-topic}", containerFactory = "factory")
    public void consumeRaw(String message) {
        indexToOpenSearch(message, "RAW");
    }

    @KafkaListener(topics = "${ratehub.calc-topic}", containerFactory = "factory")
    public void consumeCalculated(String message) {
        indexToOpenSearch(message, "CALCULATED");
    }

    private void indexToOpenSearch(String message, String sourceType) {
        try {
            String[] parts = message.split("\\|");
            if (parts.length != 4) {
                return;
            }

            Map<String, Object> doc = new HashMap<>();
            doc.put("rateName", parts[0]);
            doc.put("bid", new BigDecimal(parts[1]));
            doc.put("ask", new BigDecimal(parts[2]));
            doc.put("rateUpdateTime", parts[3]);
            doc.put("dbUpdateTime", LocalDateTime.now().format(formatter));
            doc.put("sourceType", sourceType);

            client.index(i -> i
                    .index(indexName)
                    .id(UUID.randomUUID().toString())
                    .document(doc)
            );


        } catch (Exception ex) {
        }
    }
}