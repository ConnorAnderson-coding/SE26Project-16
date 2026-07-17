package com.example.demo.config;

import com.example.demo.common.CacheNames;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisConfig {

    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer) {
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
        // 30 分钟：避免 AnalyticsEngine.computeMetrics 缓存命中后跳过 resolveSnapshot
        // 的快照自修正逻辑，使 view_count_snapshot / signup_count_snapshot 与实际值
        // 不一致时最长 30 分钟内被纠正（原 6 小时过长）。
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
