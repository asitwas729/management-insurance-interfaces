package com.example.interfacehub.application.registry;

import com.example.interfacehub.domain.interfaceconfig.InterfaceResiliencePolicy;
import com.example.interfacehub.infrastructure.persistence.InterfaceResiliencePolicyRepository;
import com.example.interfacehub.presentation.request.UpsertResiliencePolicyRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResiliencePolicyService {

    private final InterfaceResiliencePolicyRepository repository;
    private final InterfaceRegistryService interfaceRegistryService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public ResiliencePolicyService(
        InterfaceResiliencePolicyRepository repository,
        InterfaceRegistryService interfaceRegistryService,
        CircuitBreakerRegistry circuitBreakerRegistry,
        RateLimiterRegistry rateLimiterRegistry
    ) {
        this.repository = repository;
        this.interfaceRegistryService = interfaceRegistryService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "resilience-policy", key = "#interfaceCode")
    public Optional<InterfaceResiliencePolicy> findByInterfaceCode(String interfaceCode) {
        return repository.findByInterfaceCode(interfaceCode);
    }

    @Transactional
    @CacheEvict(cacheNames = "resilience-policy", key = "#interfaceCode")
    public InterfaceResiliencePolicy upsert(String interfaceCode, UpsertResiliencePolicyRequest request) {
        interfaceRegistryService.findByCode(interfaceCode);

        InterfaceResiliencePolicy policy = repository.findByInterfaceCode(interfaceCode)
            .map(existing -> {
                existing.update(
                    request.cbFailureRateThreshold(),
                    request.cbSlidingWindow(),
                    request.rateLimitPerSecond(),
                    request.timeoutMillis(),
                    request.retryMaxAttempts()
                );
                return existing;
            })
            .orElseGet(() -> InterfaceResiliencePolicy.create(
                interfaceCode,
                request.cbFailureRateThreshold(),
                request.cbSlidingWindow(),
                request.rateLimitPerSecond(),
                request.timeoutMillis(),
                request.retryMaxAttempts()
            ));

        InterfaceResiliencePolicy saved = repository.save(policy);

        circuitBreakerRegistry.remove(interfaceCode);
        rateLimiterRegistry.remove(interfaceCode);

        return saved;
    }
}
