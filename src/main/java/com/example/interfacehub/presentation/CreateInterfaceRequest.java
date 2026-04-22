package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateInterfaceRequest(
    @NotBlank String interfaceCode,
    @NotBlank String name,
    @NotNull ProtocolType protocolType,
    @NotBlank String ownerTeam,
    String externalOrg,
    @Positive Long slaMillis
) {
}
