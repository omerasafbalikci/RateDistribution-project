package com.ratedistribution.rdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ratedistribution.rdp.dto.responses.RateDataResponse;
import com.ratedistribution.rdp.model.AssetState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisConfig is responsible for configuring Redis templates used for caching or
 * storing application-specific data such as AssetState and RateDataResponse objects.
 * It sets up RedisTemplate beans with proper serialization strategies.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Configuration
public class RedisConfig {
    private static ObjectMapper redisMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    public RedisSerializer<RateDataResponse> rateDataResponseSerializer() {
        return new Jackson2JsonRedisSerializer<>(redisMapper(), RateDataResponse.class);
    }

    @Bean
    public RedisSerializer<AssetState> assetStateSerializer() {
        return new Jackson2JsonRedisSerializer<>(redisMapper(), AssetState.class);
    }

    @Bean
    public RedisTemplate<String, RateDataResponse> rateResponseRedisTemplate(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<RateDataResponse> rateDataResponseSerializer) {

        RedisTemplate<String, RateDataResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(rateDataResponseSerializer);
        return template;
    }

    @Bean
    public RedisTemplate<String, AssetState> assetStateRedisTemplate(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<AssetState> assetStateSerializer) {

        RedisTemplate<String, AssetState> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(assetStateSerializer);
        return template;
    }
}