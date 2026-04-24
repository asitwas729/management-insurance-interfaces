package com.example.interfacehub.presentation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BindInterfacePolicyRequest(
    @NotBlank String policyName,
    String partnerId,
    @Min(1) Integer priority
) {
}
