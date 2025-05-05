package com.ratedistribution.ratehub.kafka;

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

    public void sendRawTickAsString(RawTick tick, String platformName) {
        if (tick == null || tick.bid() == null || tick.ask() == null || tick.timestamp() == null) {
            log.warn("[Kafka] Skipped null or incomplete RawTick for platform: {}", platformName);
            return;
        }

        String key = platformName + "_" + tick.rateName();
        String value = String.format("%s|%s|%s|%s",
                key,
                tick.bid(),
                tick.ask(),
                tick.timestamp());
        sendPlain(rawTickTopic, key, value);
    }

    public void sendRateAsString(Rate rate) {
        if (rate == null || rate.bid() == null || rate.ask() == null || rate.timestamp() == null) {
            log.warn("[Kafka] Skipped null or incomplete Rate for {}", rate != null ? rate.rateName() : "unknown");
            return;
        }

        String key = rate.rateName();
        String value = String.format("%s|%s|%s|%s",
                rate.rateName(),
                rate.bid(),
                rate.ask(),
                rate.timestamp());
        sendPlain(calcRateTopic, key, value);
    }

    private void sendPlain(String topic, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("[Kafka] Failed to send string to {} with key {}: {}", topic, key, exception.getMessage(), exception);
            } else {
                log.debug("[Kafka] Sent string to {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
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
