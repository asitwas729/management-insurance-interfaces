package com.example.interfacehub.presentation;

import com.example.interfacehub.common.validation.ValidCron;
import jakarta.validation.constraints.NotBlank;

public record CreateInterfaceScheduleRequest(
    @NotBlank String interfaceCode,
    @NotBlank @ValidCron String cronExpression,
    String payloadTemplate
) {}
