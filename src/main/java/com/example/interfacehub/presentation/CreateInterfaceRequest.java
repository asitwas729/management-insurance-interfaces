package com.example.interfacehub.presentation;

import com.example.interfacehub.domain.interfaceconfig.ProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateInterfaceRequest(
    @NotBlank
    @Pattern(
        regexp = "^[A-Za-z0-9_]+$",
        message = "interfaceCode must contain only letters, digits, or underscores"
    )
    String interfaceCode,

    @NotBlank String name,
    @NotNull ProtocolType protocolType,
    @NotBlank String ownerTeam,
    String externalOrg,

    @Positive Long slaMillis
) {
}
