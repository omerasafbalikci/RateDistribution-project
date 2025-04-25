package com.ratedistribution.ratehub.kafka;

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
    private final String topic;

    public RateKafkaProducer(String bootstrapServers, String topic) {
        this.topic = topic;
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        p.put(ProducerConfig.BATCH_SIZE_CONFIG, 32_768);
        producer = new KafkaProducer<>(p);
    }

    public void send(String value) {
        producer.send(new ProducerRecord<>(topic, null, value), (meta, ex) -> {
            if (ex != null) log.error("Kafka send failed", ex);
        });
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }
}
