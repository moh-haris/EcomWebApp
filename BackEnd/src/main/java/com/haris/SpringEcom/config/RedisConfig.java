package com.haris.SpringEcom.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching  // Activates @Cacheable, @CachePut, @CacheEvict across the app
public class RedisConfig {

    /**
     * Defines the RedisCacheManager that Spring's @Cacheable, @CacheEvict annotations rely on.
     *
     * Serialization Strategy:
     *   - Keys   → plain String (human-readable in redis-cli)
     *   - Values → JSON via Jackson (readable + no class-cast issues across restarts)
     *
     * TTL: 10 minutes globally. Data auto-expires even if eviction annotations miss a path.
     *
     * Interview Talking Point:
     *   Using JSON serialization over default Java serialization means cached objects
     *   are version-tolerant — adding a new field to Product won't break existing cache entries.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // Configure Jackson to embed type info so it can deserialize back correctly
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.findAndRegisterModules(); // registers JavaTimeModule etc.

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))           // global TTL: 10 minutes
                .disableCachingNullValues()                 // never cache nulls
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }
}
