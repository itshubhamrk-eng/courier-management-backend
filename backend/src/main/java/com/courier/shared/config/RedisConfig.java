package com.courier.shared.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis for caching and short-lived token state.
 *
 * <p>Values are stored as JSON rather than with JDK serialization: JDK-serialized
 * payloads are unreadable in {@code redis-cli}, break on any class change, and
 * deserialize arbitrary types — a known remote-code-execution shape if the cache is
 * ever writable by anything else.
 *
 * <p><b>Key convention:</b> every company-scoped key must be prefixed
 * {@code company:{companyId}:...}. A cache key without a company segment is a
 * cross-company leak — see {@code MEMORY/ARCHITECTURE.md} §7.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    public static final String CACHE_COMPANY_CONFIG = "companyConfig";
    public static final String CACHE_SERVICEABILITY = "serviceability";
    public static final String CACHE_RATE_CARDS = "rateCards";

    /**
     * Dedicated mapper: cache entries need type information to round-trip
     * polymorphic values, which is not something the web-facing mapper should do.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /** For the token denylist and other plain string values. */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Per-cache TTLs. Nulls are not cached: caching a miss on a not-yet-created
     * resource makes it invisible until the TTL expires.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .prefixCacheNameWith("courier:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper())));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                CACHE_COMPANY_CONFIG, defaults.entryTtl(Duration.ofMinutes(10)),
                // Read on every booking; the hottest lookup in the system.
                CACHE_SERVICEABILITY, defaults.entryTtl(Duration.ofMinutes(30)),
                CACHE_RATE_CARDS, defaults.entryTtl(Duration.ofMinutes(30))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                // Redis being down must not take the API down with it.
                .transactionAware()
                .build();
    }
}
