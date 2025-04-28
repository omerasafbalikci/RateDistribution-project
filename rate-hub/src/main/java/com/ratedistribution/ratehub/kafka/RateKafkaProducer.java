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
    private final Producer<String, String> prod;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RateKafkaProducer(String bs, String topic) {
        this.topic = topic;
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bs);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        prod = new KafkaProducer<>(p);
    }

    public void sendJson(Object obj) {
        try {
            String key = (obj instanceof RawTick t) ? t.rateName() : ((Rate) obj).rateName();
            prod.send(new ProducerRecord<>(topic, key, mapper.writeValueAsString(obj)), (m, e) -> {
                if (e != null) log.error(e);
            });
        } catch (Exception e) {
            log.error(e);
        }
    }

    @Override
    public void close() {
        prod.flush();
        prod.close();
    }
}
