package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.InterfaceDefinition;
import com.example.interfacehub.domain.interfaceconfig.InterfaceStatus;
import com.example.interfacehub.domain.interfaceconfig.ProtocolType;

public record InterfaceResponse(
    Long id,
    String interfaceCode,
    String name,
    ProtocolType protocolType,
    String ownerTeam,
    String externalOrg,
    Long slaMillis,
    InterfaceStatus status
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
            definition.getStatus()
        );
    }
}
