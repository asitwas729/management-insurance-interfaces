package com.example.interfacehub.presentation;

import java.time.Instant;

public record CreateApiRequestLogRequest(
    String method,
    String path,
    String requestBody,
    Integer responseStatus,
    String responseBody,
    String errorMessage,
    Long durationMs,
    Instant occurredAt
) {
}
