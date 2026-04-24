package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;

public record ExecuteInterfaceRequest(
    @NotBlank String idempotencyKey,
    @NotNull Map<String, Object> payload,
    RuntimeEnvironment environment,
    String clientId,
    String clientSecret,
    String apiKey,
    String partnerId
) {
}
