package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.RuntimeEnvironment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

public record CreateConfigRequest(
    @NotBlank
    String endpoint,

    String authType,
    Map<String, String> headers,

    @NotNull @Positive Long timeoutMillis,
    RuntimeEnvironment environment,
    Map<String, Object> protocolConfig,
    String requestSample,
    String responseSample,
    String mappingRuleText,
    String fieldDescriptionText,
    String errorCodeGuideText,

    boolean sandboxMode,

    @Min(value = 100, message = "mockHttpStatus must be between 100 and 599")
    @Max(value = 599, message = "mockHttpStatus must be between 100 and 599")
    Integer mockHttpStatus,

    String mockResponseBody
) {
}
