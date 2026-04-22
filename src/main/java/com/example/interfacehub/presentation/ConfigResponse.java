package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.InterfaceConfigVersion;

public record ConfigResponse(
    Long configId,
    String interfaceCode,
    Integer version,
    boolean published,
    boolean sandboxMode
) {
    public static ConfigResponse from(InterfaceConfigVersion config) {
        return new ConfigResponse(
            config.getId(),
            config.getInterfaceDefinition().getInterfaceCode(),
            config.getVersion(),
            config.isPublished(),
            config.isSandboxMode()
        );
    }
}
