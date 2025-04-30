package com.ratedistribution.ratehub.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratedistribution.ratehub.model.Rate;
import com.ratedistribution.ratehub.model.RawTick;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class RateKafkaProducer implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(RateKafkaProducer.class);
    private final Producer<String, String> producer;
    private final String rawTickTopic;
    private final String calcRateTopic;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RateKafkaProducer(String bootstrapServers, String rawTickTopic, String calcRateTopic) {
        this.rawTickTopic = rawTickTopic;
        this.calcRateTopic = calcRateTopic;

        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        this.producer = new KafkaProducer<>(p);
    }

    public void sendRawTick(RawTick tick) {
        sendJson(rawTickTopic, tick.rateName(), tick);
    }

    public void sendRate(Rate rate) {
        sendJson(calcRateTopic, rate.rateName(), rate);
    }

    private void sendJson(String topic, String key, Object obj) {
        try {
            String value = mapper.writeValueAsString(obj);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("[Kafka] Failed to send to topic {} with key {}: {}", topic, key, exception.getMessage(), exception);
                } else {
                    log.debug("[Kafka] Sent to {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        } catch (Exception e) {
            log.error("[Kafka] Serialization error for key {} in topic {}: {}", key, topic, e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            producer.flush();
            producer.close();
        } catch (Exception e) {
            log.warn("[Kafka] Failed to close producer cleanly", e);
        }
    }
}
