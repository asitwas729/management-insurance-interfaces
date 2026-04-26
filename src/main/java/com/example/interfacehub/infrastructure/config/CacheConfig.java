package com.example.interfacehub.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAsyncCacheMode(true);
        manager.setCacheNames(List.of(
            "interface",
            "published-config",
            "error-catalog",
            "maintenance",
            "incident-summary"
        ));
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .recordStats());

        manager.registerCustomCache("dashboard",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(100)
                .recordStats()
                .<Object, Object>buildAsync());

        manager.registerCustomCache("resilience-policy",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1_000)
                .recordStats()
                .<Object, Object>buildAsync());

        manager.registerCustomCache("standard-message-schema",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1_000)
                .recordStats()
                .<Object, Object>buildAsync());

        manager.registerCustomCache("standard-message-rules",
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1_000)
                .recordStats()
                .<Object, Object>buildAsync());
        return manager;
    }
}
