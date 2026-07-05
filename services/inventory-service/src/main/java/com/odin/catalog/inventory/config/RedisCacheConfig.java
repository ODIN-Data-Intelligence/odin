package com.odin.catalog.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.catalog.inventory.application.dataset.ActiveTermsPolicy;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Binds the "terms-active-policy" cache to a JSON value serializer typed to {@link ActiveTermsPolicy}.
 *
 * <p>The cached value is a Java record tree, which is not {@code Serializable}, so the default
 * JDK serializer cannot be used. A type-bound {@link Jackson2JsonRedisSerializer} (using the
 * Spring-managed {@link ObjectMapper}, which has record support) round-trips it without relying on
 * polymorphic {@code @class} type hints — robust across rolling deploys where two app versions
 * share Redis.
 */
@Configuration
public class RedisCacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer termsPolicyCacheCustomizer(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<ActiveTermsPolicy> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, ActiveTermsPolicy.class);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return builder -> builder.withCacheConfiguration("terms-active-policy", config);
    }
}
