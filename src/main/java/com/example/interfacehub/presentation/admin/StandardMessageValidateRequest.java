package com.example.interfacehub.presentation.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StandardMessageValidateRequest(
    @NotBlank String schemaCode,
    @Min(1) int version,
    @NotBlank String xml,
    boolean applyRules
) {
}

