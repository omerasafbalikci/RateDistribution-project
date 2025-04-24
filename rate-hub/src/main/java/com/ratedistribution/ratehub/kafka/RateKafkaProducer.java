package com.ratedistribution.ratehub.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class RateKafkaProducer {
    private final KafkaProducer<String, String> prod;
    private final String topic;

    public RateKafkaProducer(String servers, String topic) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prod = new KafkaProducer<>(p);
        this.topic = topic;
    }

    public void send(String value) {
        prod.send(new ProducerRecord<>(topic, value));
    }

    public void close() {
        prod.close();
    }
}
