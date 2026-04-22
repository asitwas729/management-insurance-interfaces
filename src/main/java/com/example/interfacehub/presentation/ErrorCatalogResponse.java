package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.standard.ErrorCatalog;

public record ErrorCatalogResponse(
    String code,
    String domain,
    String severity,
    int httpStatus,
    boolean retriable,
    String nextAction,
    String description
) {
    public static ErrorCatalogResponse from(ErrorCatalog catalog) {
        return new ErrorCatalogResponse(
            catalog.getCode(),
            catalog.getDomain(),
            catalog.getSeverity(),
            catalog.getHttpStatus(),
            catalog.isRetriable(),
            catalog.getNextAction(),
            catalog.getDescription()
        );
    }
}
