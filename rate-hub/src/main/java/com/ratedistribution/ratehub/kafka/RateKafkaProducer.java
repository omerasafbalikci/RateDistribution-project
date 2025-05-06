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

/**
 * RateKafkaProducer is responsible for sending RawTick and Rate objects to Kafka
 * in plain string format for further processing and monitoring.
 * Supports reliability through idempotent Kafka producer configuration.
 *
 * @author Ömer Asaf BALIKÇI
 */

public class RateKafkaProducer implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(RateKafkaProducer.class);
    private final Producer<String, String> producer;
    private final String rawTickTopic;
    private final String calcRateTopic;

    /**
     * Constructs a new Kafka producer with high reliability settings.
     *
     * @param bootstrapServers Kafka server address
     * @param rawTickTopic     topic name for raw tick messages
     * @param calcRateTopic    topic name for calculated rate messages
     */
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
        log.info("[Kafka] Kafka producer initialized with bootstrap servers: {}", bootstrapServers);
    }

    /**
     * Sends a RawTick object to the raw tick Kafka topic as a delimited string.
     * Skips invalid or incomplete ticks.
     *
     * @param tick         the RawTick to send
     * @param platformName the platform identifier used as part of the message key
     */
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
        log.trace("[Kafka] Sending RawTick for {} to topic {}", key, rawTickTopic);
        sendPlain(rawTickTopic, key, value);
    }

    /**
     * Sends a Rate object to the calculated rate Kafka topic as a delimited string.
     * Skips invalid or incomplete rates.
     *
     * @param rate the Rate to send
     */
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
        log.trace("[Kafka] Sending Rate for {} to topic {}", key, calcRateTopic);
        sendPlain(calcRateTopic, key, value);
    }

    /**
     * Internal method to send a plain text message to the specified Kafka topic.
     *
     * @param topic the topic to send to
     * @param key   the key of the Kafka message
     * @param value the string value of the message
     */
    private void sendPlain(String topic, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("[Kafka] Failed to send to topic {} with key {}: {}", topic, key, exception.getMessage(), exception);
            } else {
                log.debug("[Kafka] Sent to topic {} [partition={}, offset={}], key={}",
                        metadata.topic(), metadata.partition(), metadata.offset(), key);
            }
        });
    }

    /**
     * Closes the Kafka producer gracefully.
     */
    @Override
    public void close() {
        try {
            log.info("[Kafka] Closing producer...");
            producer.flush();
            producer.close();
            log.info("[Kafka] Producer closed successfully.");
        } catch (Exception e) {
            log.warn("[Kafka] Failed to close producer cleanly: {}", e.getMessage(), e);
        }
    }
}
