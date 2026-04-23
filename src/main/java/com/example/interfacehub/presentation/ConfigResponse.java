package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;
import java.time.LocalDateTime;

public record ConfigResponse(
    Long configId,
    String interfaceCode,
    Integer version,
    String endpoint,
    String authType,
    Long timeoutMillis,
    boolean published,
    boolean sandboxMode,
    LocalDateTime createdAt
) {
    public static ConfigResponse from(InterfaceConfigVersion config) {
        return new ConfigResponse(
            config.getId(),
            config.getInterfaceDefinition().getInterfaceCode(),
            config.getVersion(),
            config.getEndpoint(),
            config.getAuthType(),
            config.getTimeoutMillis(),
            config.isPublished(),
            config.isSandboxMode(),
            config.getCreatedAt()
        );
    }
}
