package com.example.interfacehub.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
            caffeineCache("interface", Duration.ofMinutes(5)),
            caffeineCache("published-config", Duration.ofMinutes(1)),
            caffeineCache("error-catalog", Duration.ofMinutes(10)),
            caffeineCache("maintenance", Duration.ofMinutes(1)),
            caffeineCache("incident-summary", Duration.ofMinutes(30))
        ));
        return manager;
    }

    private CaffeineCache caffeineCache(String name, Duration ttl) {
        return new CaffeineCache(
            name,
            Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .recordStats()
                .build()
        );
    }
}
