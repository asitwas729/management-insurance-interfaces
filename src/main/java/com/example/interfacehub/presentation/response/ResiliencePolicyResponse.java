package com.example.interfacehub.presentation.response;

import com.example.interfacehub.domain.interfaceconfig.InterfaceResiliencePolicy;
import java.time.LocalDateTime;

public record ResiliencePolicyResponse(
    String interfaceCode,
    float cbFailureRateThreshold,
    int cbSlidingWindow,
    int rateLimitPerSecond,
    long timeoutMillis,
    int retryMaxAttempts,
    LocalDateTime updatedAt
) {
    public static ResiliencePolicyResponse from(InterfaceResiliencePolicy policy) {
        return new ResiliencePolicyResponse(
            policy.getInterfaceCode(),
            policy.getCbFailureRateThreshold(),
            policy.getCbSlidingWindow(),
            policy.getRateLimitPerSecond(),
            policy.getTimeoutMillis(),
            policy.getRetryMaxAttempts(),
            policy.getUpdatedAt()
        );
    }
}

