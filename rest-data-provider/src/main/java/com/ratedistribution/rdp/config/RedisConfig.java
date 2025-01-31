package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.AssetState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    @Bean
    public RedisTemplate<String, AssetState> assetStateRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, AssetState> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<AssetState> serializer =
                new Jackson2JsonRedisSerializer<>(AssetState.class);
        // Opsiyonel: objectMapper ile JavaTimeModule ekleme
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        return template;
    }

    @Bean
    public RedisTemplate<String, RateDataResponse> rateResponseRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, RateDataResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<RateDataResponse> serializer =
                new Jackson2JsonRedisSerializer<>(RateDataResponse.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);

        return template;
    }
}