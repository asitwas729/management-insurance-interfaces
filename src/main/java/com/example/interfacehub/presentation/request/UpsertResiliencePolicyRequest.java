package com.example.interfacehub.presentation.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

public record UpsertResiliencePolicyRequest(
    @DecimalMin("1.0") @DecimalMax("100.0") float cbFailureRateThreshold,
    @Min(5) int cbSlidingWindow,
    @Min(1) int rateLimitPerSecond,
    @Min(1) long timeoutMillis,
    @Min(0) int retryMaxAttempts
) {
}

