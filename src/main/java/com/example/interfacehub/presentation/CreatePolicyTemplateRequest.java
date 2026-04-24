package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.policy.PolicyAuthType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record CreatePolicyTemplateRequest(
    @NotBlank String policyName,
    @NotNull PolicyAuthType authType,
    @NotNull @Min(1) Long timeoutMillis,
    @NotNull @Min(0) Integer retryMaxAttempts,
    @NotNull @Min(0) Long retryIntervalMillis,
    @NotNull @Min(1) Integer rateLimitPerMinute,
    Set<String> allowedPartnerIds,
    Set<String> allowedRoles,
    boolean maskRequestPayload,
    boolean maskResponsePayload
) {
}
