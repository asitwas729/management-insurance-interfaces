package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.interfaceconfig.InterfaceStatus;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import java.time.LocalDateTime;

public record InterfaceResponse(
    Long id,
    String interfaceCode,
    String name,
    ProtocolType protocolType,
    String ownerTeam,
    String externalOrg,
    Long slaMillis,
    InterfaceStatus status,
    Integer configCount,
    LocalDateTime lastExecutedAt
) {
    public static InterfaceResponse from(InterfaceDefinition definition) {
        return new InterfaceResponse(
            definition.getId(),
            definition.getInterfaceCode(),
            definition.getName(),
            definition.getProtocolType(),
            definition.getOwnerTeam(),
            definition.getExternalOrg(),
            definition.getSlaMillis(),
            definition.getStatus(),
            null,
            null
        );
    }

    public static InterfaceResponse fromDetail(
        InterfaceDefinition definition,
        int configCount,
        LocalDateTime lastExecutedAt
    ) {
        return new InterfaceResponse(
            definition.getId(),
            definition.getInterfaceCode(),
            definition.getName(),
            definition.getProtocolType(),
            definition.getOwnerTeam(),
            definition.getExternalOrg(),
            definition.getSlaMillis(),
            definition.getStatus(),
            configCount,
            lastExecutedAt
        );
    }
}
