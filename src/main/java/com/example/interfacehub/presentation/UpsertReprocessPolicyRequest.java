package com.example.interfacehub.presentation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpsertReprocessPolicyRequest(
    @NotBlank String mode,
    @Min(0) int autoMaxAttempts,
    @Min(0) int backoffSeconds,
    @NotBlank String approvalLevel,
    boolean enabled
) {
}
