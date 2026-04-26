package com.example.interfacehub.infrastructure.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5));
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.ofEntries(
            Map.entry("interface", defaults.entryTtl(Duration.ofMinutes(5))),
            Map.entry("published-config", defaults.entryTtl(Duration.ofMinutes(1))),
            Map.entry("error-catalog", defaults.entryTtl(Duration.ofMinutes(10))),
            Map.entry("maintenance", defaults.entryTtl(Duration.ofMinutes(1))),
            Map.entry("resilience-policy", defaults.entryTtl(Duration.ofMinutes(5))),
            Map.entry("standard-message-schema", defaults.entryTtl(Duration.ofMinutes(5))),
            Map.entry("standard-message-rules", defaults.entryTtl(Duration.ofMinutes(5))),
            Map.entry("dashboard", defaults.entryTtl(Duration.ofSeconds(60)))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
