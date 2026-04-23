package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;

public record CreateInterfaceScheduleRequest(
    @NotBlank String interfaceCode,
    @NotBlank String cronExpression,
    String payloadTemplate
) {}
