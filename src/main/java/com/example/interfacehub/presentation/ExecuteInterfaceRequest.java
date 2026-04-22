package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ExecuteInterfaceRequest(
    @NotBlank String idempotencyKey,
    @NotNull Map<String, Object> payload
) {
}
