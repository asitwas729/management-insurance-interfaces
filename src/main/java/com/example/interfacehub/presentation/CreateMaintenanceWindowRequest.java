package com.example.interfacehub.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record CreateMaintenanceWindowRequest(
    @NotBlank String externalOrg,
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotBlank String suppressLevel,
    String reason
) {
}
