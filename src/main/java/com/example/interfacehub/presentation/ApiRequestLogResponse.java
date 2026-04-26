package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.logging.ApiRequestLog;
import java.time.LocalDateTime;

public record ApiRequestLogResponse(
    Long id,
    String actor,
    String source,
    String method,
    String path,
    Integer responseStatus,
    String errorMessage,
    Long durationMs,
    LocalDateTime occurredAt,
    LocalDateTime createdAt
) {
    public static ApiRequestLogResponse from(ApiRequestLog entry) {
        return new ApiRequestLogResponse(
            entry.getId(),
            entry.getActor(),
            entry.getSource(),
            entry.getMethod(),
            entry.getPath(),
            entry.getResponseStatus(),
            entry.getErrorMessage(),
            entry.getDurationMs(),
            entry.getOccurredAt(),
            entry.getCreatedAt()
        );
    }
}

