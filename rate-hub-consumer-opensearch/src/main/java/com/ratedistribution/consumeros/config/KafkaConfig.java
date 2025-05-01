package com.ratedistribution.consumeros.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String servers;

    @Bean
    public Map<String, Object> consumerProps() {
        Map<String, Object> m = new HashMap<>();
        m.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        m.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        m.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        m.put(ConsumerConfig.GROUP_ID_CONFIG, "rate-os");
        return m;
    }

    @Bean
    public ConsumerFactory<String, String> cf() {
        return new DefaultKafkaConsumerFactory<>(consumerProps());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> factory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
        f.setConsumerFactory(cf());
        return f;
    }
}
