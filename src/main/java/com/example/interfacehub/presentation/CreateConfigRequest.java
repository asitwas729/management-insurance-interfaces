package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record CreateConfigRequest(
    @NotBlank String endpoint,
    String authType,
    Map<String, String> headers,
    @NotNull @Positive Long timeoutMillis,
    boolean sandboxMode,
    Integer mockHttpStatus,
    String mockResponseBody
) {
}
