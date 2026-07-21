package com.example.demo.config;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.example.demo.common.CacheNames;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisConfig {

    @Bean
    public RedisSerializer<Object> redisJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object source) throws SerializationException {
                if (source == null) {
                    return new byte[0];
                }
                try {
                    return objectMapper.writeValueAsBytes(source);
                } catch (JsonProcessingException ex) {
                    throw new SerializationException("Failed to serialize object to Redis", ex);
                }
            }

            @Override
            public Object deserialize(byte[] source) throws SerializationException {
                if (source == null || source.length == 0) {
                    return null;
                }
                try {
                    return objectMapper.readValue(source, Object.class);
                } catch (IOException ex) {
                    throw new SerializationException("Failed to deserialize object from Redis", ex);
                }
            }
        };
    }

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisSerializer<Object> redisJsonSerializer) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer))
                .prefixCacheNameWith("campus:")
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CacheNames.USER_PROFILE, defaults.entryTtl(Duration.ofHours(1)));
        perCache.put(CacheNames.ACTIVITY_DETAIL, defaults.entryTtl(Duration.ofMinutes(15)));
        perCache.put(CacheNames.ACTIVITY_HOT_LIST, defaults.entryTtl(Duration.ofMinutes(10)));
        perCache.put(CacheNames.FEEDBACK_BY_ACTIVITY, defaults.entryTtl(Duration.ofMinutes(20)));
        perCache.put(CacheNames.ACTIVITY_RECORD, defaults.entryTtl(Duration.ofHours(2)));
        // 分析结果短时缓存；报名、签到、收藏、浏览等写操作会主动失效对应活动缓存。
        perCache.put(CacheNames.ANALYTICS_ACTIVITY, defaults.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
